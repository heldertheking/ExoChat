package com.heldertheking.exochat.app.ratelimit;

import java.time.Duration;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// Settings of the rate limiting (ADR-014), prefix `exochat.ratelimit`.
///
/// @param enabled global kill switch, when false nothing is limited
/// @param failOpen what to do when the store fails: true lets the call pass (availability first), false rejects
///     it
/// @param trustedProxyHeader name of a header set by a trusted reverse proxy that carries the client address,
///     for example `x-forwarded-for`. Blank (the default): the header is ignored and the address of the socket
///     is used, because any client can send such a header. Only set it when every request passes the proxy,
///     which overwrites the header; the last entry of the header is used
/// @param maxKeys upper bound of the counters kept in memory, the oldest ones are dropped beyond it
/// @param policies policies by name; an entry changes the fields it sets of the built-in policy of that name or
///     defines a new policy, which needs all fields
@ConfigurationProperties("exochat.ratelimit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean failOpen,
        @Nullable String trustedProxyHeader,
        @DefaultValue("100000") long maxKeys,
        @DefaultValue Map<String, Policy> policies) {

    /// One policy, every field is optional here and falls back to the built-in policy of the same name.
    ///
    /// @param limit calls allowed per window
    /// @param window length of the window
    /// @param key what the counter is keyed by
    /// @param requestKeyLimit calls allowed per window for the same key taken from the request body (the
    ///     email address of a login), 0 for none
    public record Policy(
            @Nullable Integer limit,
            @Nullable Duration window,
            @Nullable KeyStrategy key,
            @Nullable Integer requestKeyLimit) {}
}
