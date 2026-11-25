package com.heldertheking.exochat.app.ratelimit;

import java.time.Duration;

/// Counters of the rate limiter. The implementation must be safe for concurrent use and must bound its memory.
/// A Valkey or Redis implementation can replace the in-memory one (ADR-014); then every instance of the
/// application shares the counters.
public interface RateLimitStore {

    /// Counts one call against `key` and says whether it is within `limit` calls per `window`.
    ///
    /// @param key opaque, contains no personal data
    /// @param limit calls allowed in a window
    /// @param window length of the window, starts with the first counted call
    /// @return the decision, a rejected call is not counted
    Decision tryAcquire(String key, int limit, Duration window);

    /// The answer of a store.
    ///
    /// @param allowed false if the limit is reached
    /// @param retryAfter time until the counter resets, only meaningful when not allowed
    record Decision(boolean allowed, Duration retryAfter) {}
}
