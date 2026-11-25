package com.heldertheking.quela;

import static com.heldertheking.quela.QueryFieldParser.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.ZoneId;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QueryFieldParserTest {

    @Nested
    class When_coercing_and_decoercing_values {
        @Test
        void expect_same_value_when_coercing_and_decoercing_integers() {
            var value = BigDecimal.valueOf(123);
            assertThat(INTEGER.getQueryPropertyParser()
                            .decoerce(INTEGER.getQueryPropertyParser().coerce(value)))
                    .isEqualTo(value);
        }

        @Test
        void expect_same_value_when_coercing_and_decoercing_longs() {
            var value = BigDecimal.valueOf(1234);
            assertThat(LONG.getQueryPropertyParser()
                            .decoerce(LONG.getQueryPropertyParser().coerce(value)))
                    .isEqualTo(value);
        }

        @Test
        void expect_same_value_when_coercing_and_decoercing_doubles() {
            var value = new BigDecimal("1.1");
            assertThat(DOUBLE.getQueryPropertyParser()
                            .decoerce(DOUBLE.getQueryPropertyParser().coerce(value)))
                    .isCloseTo(value, Offset.offset(BigDecimal.valueOf(1e-9D)));
        }

        @Test
        void expect_same_value_when_coercing_and_decoercing_booleans() {
            assertThat(BOOLEAN.getQueryPropertyParser()
                            .decoerce(BOOLEAN.getQueryPropertyParser().coerce(true)))
                    .isTrue();
            assertThat(BOOLEAN.getQueryPropertyParser()
                            .decoerce(BOOLEAN.getQueryPropertyParser().coerce(false)))
                    .isFalse();
        }

        @Test
        void expect_same_value_when_coercing_and_decoercing_dates() {
            var date = "2024-12-13";
            assertThat(DATE.getQueryPropertyParser()
                            .decoerce(DATE.getQueryPropertyParser().coerce(date)))
                    .isEqualTo(date);
        }

        @Test
        void expect_same_value_when_coercing_and_decoercing_datetimes() {
            var date = "2024-12-13T13:45:59Z";
            assertThat(DATE_TIME
                            .getQueryPropertyParser()
                            .decoerce(DATE_TIME.getQueryPropertyParser().coerce(date)))
                    .isEqualTo(date);
        }

        @Test
        void expect_same_value_when_coercing_and_decoercing_local_datetimes() {
            var date = "2024-12-13T13:45:59Z";
            var type = LOCAL_DATE_TIME.atZone(ZoneId.of("Europe/Zurich"));
            assertThat(type.getQueryPropertyParser()
                            .decoerce(type.getQueryPropertyParser().coerce(date)))
                    .isEqualTo(date);
        }
    }
}
