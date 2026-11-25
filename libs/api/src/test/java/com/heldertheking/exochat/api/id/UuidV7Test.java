package com.heldertheking.exochat.api.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class UuidV7Test {

    /// clock whose time is set by the test
    static final class SettableClock extends Clock {
        final AtomicLong millis = new AtomicLong();

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
            return Instant.ofEpochMilli(millis.get());
        }
    }

    @Test
    void next_should_produceVersion7Variant2() {
        var id = UuidV7.next();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(UuidV7.isV7(id)).isTrue();
        assertThat(UuidV7.PATTERN.matcher(id.toString())).matches();
    }

    @Test
    void timestamp_should_returnCreationTime() {
        var clock = new SettableClock();
        clock.millis.set(1_700_000_123_456L);

        var id = new UuidV7(clock, new SecureRandom()).generate();

        assertThat(UuidV7.timestamp(id)).isEqualTo(Instant.ofEpochMilli(1_700_000_123_456L));
    }

    @Test
    void generate_should_beStrictlyIncreasing_within_sameMillisecond() {
        var clock = new SettableClock();
        clock.millis.set(1_700_000_000_000L);
        var generator = new UuidV7(clock, new SecureRandom());

        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 10_000; i++) { // more than the 4096 counter values of one millisecond
            ids.add(generator.generate());
        }

        for (int i = 1; i < ids.size(); i++) {
            assertThat(compare(ids.get(i - 1), ids.get(i))).isNegative();
        }
        assertThat(new HashSet<>(ids)).hasSameSizeAs(ids);
    }

    @Test
    void generate_should_stayMonotonic_when_clockGoesBackwards() {
        var clock = new SettableClock();
        clock.millis.set(1_700_000_000_000L);
        var generator = new UuidV7(clock, new SecureRandom());
        var before = generator.generate();

        clock.millis.set(1_699_999_990_000L);
        var after = generator.generate();

        assertThat(compare(before, after)).isNegative();
    }

    @Test
    void generate_should_sortByTime_across_milliseconds() {
        var clock = new SettableClock();
        var generator = new UuidV7(clock, new SecureRandom());
        clock.millis.set(1_700_000_000_000L);
        var first = generator.generate();
        clock.millis.set(1_700_000_000_001L);
        var second = generator.generate();

        assertThat(compare(first, second)).isNegative();
        assertThat(first.toString()).isLessThan(second.toString()); // text form sorts the same
    }

    @Test
    void next_should_beUnique_across_threads() throws Exception {
        var ids = ConcurrentHashMap.<UUID>newKeySet();
        var threads = new ArrayList<Thread>();
        for (int t = 0; t < 8; t++) {
            threads.add(Thread.ofPlatform().start(() -> {
                for (int i = 0; i < 5_000; i++) {
                    ids.add(UuidV7.next());
                }
            }));
        }
        for (var thread : threads) {
            thread.join();
        }
        assertThat(ids).hasSize(40_000);
    }

    @Test
    void parse_and_timestamp_should_rejectOtherUuids() {
        var v4 = UUID.randomUUID();

        assertThat(UuidV7.isV7(v4)).isFalse();
        assertThatThrownBy(() -> UuidV7.timestamp(v4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UuidV7.parse(v4.toString())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UuidV7.parse(UuidV7.next().toString().toUpperCase()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UuidV7.parse("alice")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_should_roundTrip() {
        var id = UuidV7.next();
        assertThat(UuidV7.parse(id.toString())).isEqualTo(id);
    }

    /// unsigned comparison, like a byte-wise database comparison
    private static int compare(UUID a, UUID b) {
        int c = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return c != 0 ? c : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
