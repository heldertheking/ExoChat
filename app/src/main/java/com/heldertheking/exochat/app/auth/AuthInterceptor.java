package com.heldertheking.exochat.app.auth;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

import com.heldertheking.exochat.auth.v1alpha1.AuthServiceGrpc;
import com.heldertheking.exochat.health.v1alpha1.HealthServiceGrpc;

import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/// Authenticates every gRPC call except the few public ones: the call needs an `authorization: Bearer <token>`
/// header with a valid access token (ADR-012). The verified identity is available to the handler as [Caller].
/// The token is verified without a database lookup, so a revoked session works until its access token expires.
@Component
@GlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class AuthInterceptor implements ServerInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(AuthInterceptor.class);
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER = "Bearer ";

    /// Methods that are callable without an access token.
    private static final Set<String> PUBLIC_METHODS = Set.of(
            AuthServiceGrpc.getRegisterUserMethod().getFullMethodName(),
            AuthServiceGrpc.getAuthenticateUserMethod().getFullMethodName(),
            AuthServiceGrpc.getRefreshSessionMethod().getFullMethodName(),
            HealthServiceGrpc.getCheckMethod().getFullMethodName());

    private final AccessTokens accessTokens;

    AuthInterceptor(AccessTokens accessTokens) {
        this.accessTokens = accessTokens;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if (PUBLIC_METHODS.contains(call.getMethodDescriptor().getFullMethodName())) {
            return next.startCall(call, headers);
        }
        var header = headers.get(AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return reject(call, "missing access token");
        }
        try {
            var claims = accessTokens.verify(header.substring(BEARER.length()).trim());
            var context = io.grpc.Context.current()
                    .withValue(Caller.KEY, new Caller(claims.userId(), claims.sessionId(), claims.accountId()));
            return Contexts.interceptCall(context, call, headers, next);
        } catch (AccessTokens.InvalidAccessTokenException e) {
            LOG.debug("access token rejected: {}", e.getMessage());
            return reject(call, "invalid access token");
        }
    }

    private static <ReqT, RespT> ServerCall.Listener<ReqT> reject(ServerCall<ReqT, RespT> call, String description) {
        call.close(Status.UNAUTHENTICATED.withDescription(description), new Metadata());
        return new ServerCall.Listener<>() {};
    }
}
