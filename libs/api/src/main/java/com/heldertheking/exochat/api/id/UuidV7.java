package com.heldertheking.exochat.api.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/// Generator and helpers for time-ordered UUIDs ([RFC 9562](https://www.rfc-editor.org/rfc/rfc9562) version 7).
///
/// Layout: 48 bit unix timestamp in milliseconds, 4 bit version, 12 bit counter, 2 bit variant, 62 bit random.
/// Ids from one generator are strictly increasing, even within the same millisecond and if the clock steps
/// backwards, so they sort in creation order and append to the end of a primary key index.
///
/// Use the shared [#next()] for application code. Create your own instance with a [Clock] in tests.
public final class UuidV7 {
    /// Canonical lowercase form of a version 7 UUID.
    public static final Pattern PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    private static final UuidV7 SHARED = new UuidV7(Clock.systemUTC(), new SecureRandom());
    private static final int MAX_COUNTER = 0xFFF;
    private static final long MASK_48 = 0xFFFF_FFFF_FFFFL;
    private static final long MASK_62 = 0x3FFF_FFFF_FFFF_FFFFL;

    private final Clock clock;
    private final SecureRandom random;
    private long lastMillis = -1;
    private int counter;

    public UuidV7(Clock clock, SecureRandom random) {
        this.clock = clock;
        this.random = random;
    }

    /// Next id from the shared, thread safe generator.
    public static UUID next() {
        return SHARED.generate();
    }

    /// Generates the next id. Thread safe.
    public synchronized UUID generate() {
        long millis = clock.millis();
        if (millis > lastMillis) {
            lastMillis = millis;
            // random start leaves room to count up within this millisecond
            counter = random.nextInt(MAX_COUNTER / 2);
        } else if (counter < MAX_COUNTER) {
            counter++;
        } else {
            // counter exhausted (or clock went backwards): borrow the next millisecond to stay monotonic
            lastMillis++;
            counter = random.nextInt(MAX_COUNTER / 2);
        }
        long msb = ((lastMillis & MASK_48) << 16) | (0x7L << 12) | counter;
        long lsb = (0x2L << 62) | (random.nextLong() & MASK_62);
        return new UUID(msb, lsb);
    }

    /// true if the UUID is a version 7 (RFC 9562 variant) UUID.
    public static boolean isV7(UUID uuid) {
        return uuid.version() == 7 && uuid.variant() == 2;
    }

    /// The creation time encoded in a version 7 UUID.
    ///
    /// @throws IllegalArgumentException if the UUID is not version 7
    public static Instant timestamp(UUID uuid) {
        if (!isV7(uuid)) {
            throw new IllegalArgumentException("not a UUIDv7: " + uuid);
        }
        return Instant.ofEpochMilli(uuid.getMostSignificantBits() >>> 16);
    }

    /// Parses the canonical lowercase string form of a version 7 UUID.
    ///
    /// @throws IllegalArgumentException if the string is not a lowercase, canonical UUIDv7
    public static UUID parse(String value) {
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("not a UUIDv7: '%s'".formatted(value));
        }
        return UUID.fromString(value);
    }
}
