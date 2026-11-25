package com.heldertheking.exochat.app.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.grpc.server.security.GrpcSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import com.heldertheking.exochat.app.security.AuthenticatedUser;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/// Limits the calls of the RPCs annotated with [RateLimited] (ADR-014).
///
/// It runs right after the authentication interceptor of Spring Security ([#ORDER]), so the user of the call is
/// in the security context and a flood of calls with bad tokens never reaches a counter. The public calls
/// (register, login, refresh) pass authentication without a token and are keyed by address. The count happens
/// when the first message of the call arrived, because the request body may be a key; the handler gets nothing
/// of a rejected call.
final class RateLimitInterceptor implements ServerInterceptor, Ordered {
    /// Just after `AuthenticationProcessInterceptor`, which is at `CONTEXT_FILTER_ORDER - 10`.
    static final int ORDER = GrpcSecurity.CONTEXT_FILTER_ORDER - 5;

    static final Metadata.Key<String> RETRY_AFTER = Metadata.Key.of("retry-after", Metadata.ASCII_STRING_MARSHALLER);

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final boolean enabled;
    private final boolean failOpen;
    private final Map<String, RateLimitPolicies.Policy> policies;
    private final Map<Class<?>, RequestKeyExtractor<?>> extractors;
    private final RateLimitStore store;
    private final ClientAddresses addresses;

    RateLimitInterceptor(
            boolean enabled,
            boolean failOpen,
            Map<String, RateLimitPolicies.Policy> policies,
            Map<Class<?>, RequestKeyExtractor<?>> extractors,
            RateLimitStore store,
            ClientAddresses addresses) {
        this.enabled = enabled;
        this.failOpen = failOpen;
        this.policies = policies;
        this.extractors = extractors;
        this.store = store;
        this.addresses = addresses;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        var policy = enabled ? policies.get(call.getMethodDescriptor().getFullMethodName()) : null;
        if (policy == null) {
            return next.startCall(call, headers);
        }
        var peer = policy.key() == KeyStrategy.USER ? "" : addresses.resolve(call.getAttributes(), headers);
        return new Guard<>(next.startCall(call, headers), call, policy, peer);
    }

    /// Counts the call before it reaches the handler.
    private final class Guard<ReqT, RespT> extends ForwardingServerCallListener<ReqT> {
        private final ServerCall.Listener<ReqT> delegate;
        private final ServerCall<ReqT, RespT> call;
        private final RateLimitPolicies.Policy policy;
        private final String peer;
        private boolean checked;
        private boolean rejected;

        Guard(
                ServerCall.Listener<ReqT> delegate,
                ServerCall<ReqT, RespT> call,
                RateLimitPolicies.Policy policy,
                String peer) {
            this.delegate = delegate;
            this.call = call;
            this.policy = policy;
            this.peer = peer;
        }

        @Override
        protected ServerCall.Listener<ReqT> delegate() {
            return delegate;
        }

        @Override
        public void onMessage(ReqT message) {
            check(message);
            if (!rejected) {
                super.onMessage(message);
            }
        }

        @Override
        public void onHalfClose() {
            check(null);
            if (!rejected) {
                super.onHalfClose();
            }
        }

        @Override
        public void onReady() {
            if (!rejected) {
                super.onReady();
            }
        }

        @Override
        public void onCancel() {
            if (!rejected) {
                super.onCancel();
            }
        }

        @Override
        public void onComplete() {
            if (!rejected) {
                super.onComplete();
            }
        }

        private void check(@Nullable ReqT message) {
            if (checked) {
                return;
            }
            checked = true;
            var retryAfter = admit(message);
            if (retryAfter != null) {
                rejected = true;
                reject(retryAfter);
            }
        }

        /// Null if the call may pass, otherwise the time until it may try again.
        private @Nullable Duration admit(@Nullable ReqT message) {
            var user = policy.key() == KeyStrategy.PEER ? null : currentUser();
            if (policy.key() != KeyStrategy.PEER && user == null) {
                // fail closed: a user key without a user would put every caller in one bucket
                call.close(Status.UNAUTHENTICATED.withDescription("not authenticated"), new Metadata());
                rejected = true;
                return null;
            }
            var key = switch (policy.key()) {
                case PEER -> "p:" + peer;
                case USER -> "u:" + user;
                case PEER_AND_USER -> "p:" + peer + "|u:" + user;
            };
            var denied = acquire(policy.name() + "|" + key, policy.limit());
            if (denied != null) {
                return denied;
            }
            if (policy.requestKeyLimit() > 0 && message != null) {
                var requestKey = requestKey(message);
                if (requestKey != null) {
                    return acquire(policy.name() + "|r:" + requestKey, policy.requestKeyLimit());
                }
            }
            return null;
        }

        private @Nullable Duration acquire(String key, int limit) {
            try {
                var decision = store.tryAcquire(key, limit, policy.window());
                return decision.allowed() ? null : decision.retryAfter();
            } catch (RuntimeException e) {
                log.error(
                        "rate limit store failed for policy {}, failing {}",
                        policy.name(),
                        failOpen ? "open" : "closed",
                        e);
                return failOpen ? null : policy.window();
            }
        }

        @SuppressWarnings("unchecked")
        private @Nullable String requestKey(ReqT message) {
            var extractor = (RequestKeyExtractor<ReqT>) extractors.get(message.getClass());
            var raw = extractor == null ? null : extractor.extract(message);
            return raw == null ? null : sha256(raw);
        }

        private void reject(Duration retryAfter) {
            var seconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
            log.warn(
                    "rate limit '{}' exceeded on {}, retry in {}s",
                    policy.name(),
                    call.getMethodDescriptor().getFullMethodName(),
                    seconds);
            var trailers = new Metadata();
            trailers.put(RETRY_AFTER, Long.toString(seconds));
            call.close(Status.RESOURCE_EXHAUSTED.withDescription("too many requests"), trailers);
        }
    }

    private static @Nullable String currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.getAttrs().userId().toString();
        }
        return null;
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
