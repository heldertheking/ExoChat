package com.heldertheking.exochat.app.ratelimit;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;

/// The policies in force and the mapping from gRPC methods to them, both worked out once at startup.
final class RateLimitPolicies {

    /// A policy with all its values.
    ///
    /// @param requestKeyLimit 0 if the request body is not a key
    record Policy(String name, int limit, Duration window, KeyStrategy key, int requestKeyLimit) {}

    /// The policies the code ships with, `exochat.ratelimit.policies` overrides them field by field. A human who
    /// mistypes a password a few times stays far below these, a script does not.
    static final Map<String, Policy> BUILT_IN = Map.of(
            // registering: few accounts per address and hour
            "register", new Policy("register", 20, Duration.ofHours(1), KeyStrategy.PEER, 0),
            // login: per address (shared by a household or an office) and per email address, so that one account
            // cannot be guessed from many addresses; the window is short enough to ride out a typo spree
            "login", new Policy("login", 30, Duration.ofMinutes(5), KeyStrategy.PEER, 10),
            "refresh", new Policy("refresh", 60, Duration.ofMinutes(1), KeyStrategy.PEER, 0),
            "change-password", new Policy("change-password", 5, Duration.ofMinutes(15), KeyStrategy.USER, 0),
            "switch-account", new Policy("switch-account", 30, Duration.ofMinutes(1), KeyStrategy.USER, 0),
            "user-update", new Policy("user-update", 30, Duration.ofMinutes(1), KeyStrategy.USER, 0),
            "user-delete", new Policy("user-delete", 3, Duration.ofHours(1), KeyStrategy.USER, 0),
            "account-create", new Policy("account-create", 10, Duration.ofHours(1), KeyStrategy.USER, 0),
            "relationship-request", new Policy("relationship-request", 60, Duration.ofHours(1), KeyStrategy.USER, 0),
            "relationship-block", new Policy("relationship-block", 30, Duration.ofMinutes(1), KeyStrategy.USER, 0));

    private RateLimitPolicies() {}

    /// The built-in policies with the configured values on top.
    ///
    /// @throws IllegalStateException a policy is incomplete or has a value that makes no sense
    static Map<String, Policy> resolve(RateLimitProperties properties) {
        var result = new LinkedHashMap<>(BUILT_IN);
        properties.policies().forEach((name, configured) -> {
            var base = Optional.ofNullable(BUILT_IN.get(name));
            var limit = first(configured.limit(), base.map(Policy::limit), name, "limit");
            var window = first(configured.window(), base.map(Policy::window), name, "window");
            var key = first(configured.key(), base.map(Policy::key), name, "key");
            var requestKeyLimit = configured.requestKeyLimit() != null
                    ? configured.requestKeyLimit()
                    : base.map(Policy::requestKeyLimit).orElse(0);
            if (limit < 1 || window.isZero() || window.isNegative() || requestKeyLimit < 0) {
                throw new IllegalStateException("rate limit policy '" + name + "' needs limit >= 1 and a positive"
                        + " window and request-key-limit >= 0");
            }
            result.put(name, new Policy(name, limit, window, key, requestKeyLimit));
        });
        return result;
    }

    private static <T> T first(@Nullable T configured, Optional<T> builtIn, String policy, String field) {
        if (configured != null) {
            return configured;
        }
        return builtIn.orElseThrow(() ->
                new IllegalStateException("rate limit policy '" + policy + "' is not built in and has no " + field));
    }

    /// Maps the full gRPC method names of the annotated methods to their policy. The Java method of an RPC `Foo`
    /// is named `foo` (generated `*ImplBase`).
    ///
    /// @throws IllegalStateException an annotation names an unknown policy or sits on a method that is no RPC
    static Map<String, Policy> bind(Collection<BindableService> services, Map<String, Policy> policies) {
        var bound = new HashMap<String, Policy>();
        for (var service : services) {
            var type = ClassUtils.getUserClass(service);
            var rpcs = new HashMap<String, MethodDescriptor<?, ?>>();
            for (var method : service.bindService().getServiceDescriptor().getMethods()) {
                var rpc = method;
                rpcs.put(javaName(rpc), rpc);
            }
            for (Method method : type.getMethods()) {
                var annotation = method.getAnnotation(RateLimited.class);
                if (annotation == null) {
                    continue;
                }
                var policy = policies.get(annotation.value());
                if (policy == null) {
                    throw new IllegalStateException("@RateLimited(\"" + annotation.value() + "\") on "
                            + type.getSimpleName() + "." + method.getName() + " names a policy that is not configured");
                }
                var rpc = rpcs.get(method.getName());
                if (rpc == null) {
                    throw new IllegalStateException(
                            "@RateLimited on " + type.getSimpleName() + "." + method.getName() + " is not an RPC");
                }
                bound.put(rpc.getFullMethodName(), policy);
            }
        }
        return Map.copyOf(bound);
    }

    private static String javaName(MethodDescriptor<?, ?> rpc) {
        var name = MethodDescriptor.extractBareMethodName(rpc.getFullMethodName());
        if (name == null || name.isEmpty()) {
            throw new IllegalStateException("gRPC method without a name: " + rpc.getFullMethodName());
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
