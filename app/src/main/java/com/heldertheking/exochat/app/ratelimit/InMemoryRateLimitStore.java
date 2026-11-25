package com.heldertheking.exochat.app.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

/// Fixed window counters in a bounded Caffeine cache. A counter lives until its window ends and the cache never
/// holds more than `maxKeys` of them, so a flood of distinct keys cannot grow the memory without limit. Beyond
/// the bound the cache drops the least valuable counters, so an attacker with very many keys could reset the
/// counter of someone else; the bound is therefore generous. A fixed window lets a caller use the limit twice
/// around the end of a window, which is acceptable here.
///
/// The decision uses the injected [Clock] only, the cache is merely told the same time for its expiry.
class InMemoryRateLimitStore implements RateLimitStore {
    private record Counter(Instant windowEnd, int count) {}

    private final Clock clock;
    private final Cache<String, Counter> counters;

    InMemoryRateLimitStore(Clock clock, long maxKeys) {
        this.clock = clock;
        this.counters = Caffeine.newBuilder()
                .maximumSize(maxKeys)
                .executor(Runnable::run)
                .ticker(() -> Duration.between(Instant.EPOCH, clock.instant()).toNanos())
                .expireAfter(new Expiry<String, Counter>() {
                    @Override
                    public long expireAfterCreate(String key, Counter value, long currentTime) {
                        return remainingNanos(value);
                    }

                    @Override
                    public long expireAfterUpdate(String key, Counter value, long currentTime, long currentDuration) {
                        return remainingNanos(value);
                    }

                    @Override
                    public long expireAfterRead(String key, Counter value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    private long remainingNanos(Counter counter) {
        return Math.max(
                1, Duration.between(clock.instant(), counter.windowEnd()).toNanos());
    }

    @Override
    public Decision tryAcquire(String key, int limit, Duration window) {
        var now = clock.instant();
        var decision = new AtomicReference<Decision>();
        counters.asMap().compute(key, (k, current) -> {
            var counter =
                    current == null || !now.isBefore(current.windowEnd()) ? new Counter(now.plus(window), 0) : current;
            if (counter.count() < limit) {
                decision.set(new Decision(true, Duration.ZERO));
                return new Counter(counter.windowEnd(), counter.count() + 1);
            }
            decision.set(new Decision(false, Duration.between(now, counter.windowEnd())));
            return counter;
        });
        return decision.get();
    }

    /// Number of counters held, after pending evictions.
    long size() {
        counters.cleanUp();
        return counters.estimatedSize();
    }
}
