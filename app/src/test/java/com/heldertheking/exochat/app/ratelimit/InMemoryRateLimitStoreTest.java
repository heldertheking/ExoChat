package com.heldertheking.exochat.app.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class InMemoryRateLimitStoreTest {
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final class TestClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-25T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final InMemoryRateLimitStore store = new InMemoryRateLimitStore(clock, 1000);

    @Test
    void allowsUpToTheLimitThenRejectsWithTheTimeUntilTheReset() {
        for (int i = 0; i < 3; i++) {
            assertThat(store.tryAcquire("k", 3, WINDOW).allowed()).isTrue();
        }
        clock.advance(Duration.ofSeconds(20));

        var denied = store.tryAcquire("k", 3, WINDOW);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void rejectedCallsAreNotCounted() {
        store.tryAcquire("k", 1, WINDOW);
        for (int i = 0; i < 10; i++) {
            assertThat(store.tryAcquire("k", 1, WINDOW).allowed()).isFalse();
        }
        clock.advance(WINDOW);

        assertThat(store.tryAcquire("k", 1, WINDOW).allowed()).isTrue();
    }

    @Test
    void theCounterResetsWhenTheWindowEnds() {
        store.tryAcquire("k", 1, WINDOW);
        clock.advance(WINDOW.minusMillis(1));
        assertThat(store.tryAcquire("k", 1, WINDOW).allowed()).isFalse();

        clock.advance(Duration.ofMillis(1));

        assertThat(store.tryAcquire("k", 1, WINDOW).allowed()).isTrue();
        assertThat(store.tryAcquire("k", 1, WINDOW).allowed()).isFalse();
    }

    @Test
    void keysAreIndependent() {
        assertThat(store.tryAcquire("a", 1, WINDOW).allowed()).isTrue();
        assertThat(store.tryAcquire("a", 1, WINDOW).allowed()).isFalse();

        assertThat(store.tryAcquire("b", 1, WINDOW).allowed()).isTrue();
    }

    @Test
    void expiredCountersAreDropped() {
        for (int i = 0; i < 100; i++) {
            store.tryAcquire("k" + i, 1, WINDOW);
        }
        assertThat(store.size()).isEqualTo(100);

        clock.advance(WINDOW.plusSeconds(1));

        assertThat(store.size()).isZero();
    }

    @Test
    void theNumberOfCountersIsBounded() {
        var bounded = new InMemoryRateLimitStore(clock, 50);

        for (int i = 0; i < 10_000; i++) {
            bounded.tryAcquire("flood-" + i, 5, WINDOW);
        }

        assertThat(bounded.size()).isLessThanOrEqualTo(50);
    }

    @Test
    void neverAllowsMoreThanTheLimitUnderConcurrency() throws Exception {
        var allowed = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            var futures = IntStream.range(0, 1000)
                    .mapToObj(i -> pool.submit(() -> {
                        if (store.tryAcquire("hot", 100, WINDOW).allowed()) {
                            allowed.incrementAndGet();
                        }
                    }))
                    .toList();
            for (var future : futures) {
                future.get();
            }
        }

        assertThat(allowed).hasValue(100);
    }
}
