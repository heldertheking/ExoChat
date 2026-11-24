package com.heldertheking.quela;

import org.jooq.Condition;
import org.jooq.Field;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;

/// FieldType defines the types of fields that can be used in a query. Both the [SearchParser]
/// and [OrderByParser] use this interface to determine how to parse and compare
/// fields.
public sealed interface QueryFieldParser<T> {
    SingleDBFieldProperty<String, StringQueryFieldParser<String>> STRING = new SingleDBFieldProperty<>() {

        @Override
        public StringQueryFieldParser<String> getQueryPropertyParser() {
            return new StringQueryFieldParser<>() {
                @Override
                public String coerce(String value) {
                    return value;
                }

                @Override
                public String decoerce(String value) {
                    return value;
                }
            };
        }

        @Override
        public ConditionFactory<String> conditionFactoryForField(Field<String> field) {
            return new FieldConditionFactory<>(field) {
                @Override
                public Condition eq(String value) {
                    if (value.contains("*")) {
                        return this.field.like(value.replace("*", "%"));
                    }
                    return this.field.eq(value);
                }
            };
        }
    };
    SingleDBFieldProperty<Integer, NumberQueryFieldParser<Integer>> INTEGER = () -> new NumberQueryFieldParser<>() {
        @Override
        public Integer coerce(BigDecimal value) {
            if (value.scale() > 0) {
                throw new FieldParseException("failed to parse %s as an integer".formatted(value));
            }
            return value.intValue();
        }

        @Override
        public BigDecimal decoerce(Integer value) {
            return BigDecimal.valueOf(value);
        }
    };
    SingleDBFieldProperty<Long, NumberQueryFieldParser<Long>> LONG = () -> new NumberQueryFieldParser<>() {
        @Override
        public Long coerce(BigDecimal value) {
            if (value.scale() > 0) {
                throw new FieldParseException("failed to parse %s as an long".formatted(value));
            }
            return value.longValue();
        }

        @Override
        public BigDecimal decoerce(Long value) {
            return BigDecimal.valueOf(value);
        }
    };
    SingleDBFieldProperty<Double, NumberQueryFieldParser<Double>> DOUBLE = () -> new NumberQueryFieldParser<>() {
        @Override
        public Double coerce(BigDecimal value) {
            return value.doubleValue();
        }

        @Override
        public BigDecimal decoerce(Double value) {
            return BigDecimal.valueOf(value);
        }
    };
    SingleDBFieldProperty<Boolean, BooleanQueryFieldParser<Boolean>> BOOLEAN = () -> new BooleanQueryFieldParser<>() {
        @Override
        public Boolean coerce(boolean value) {
            return value;
        }

        @Override
        public boolean decoerce(Boolean value) {
            return value;
        }
    };
    SingleDBFieldProperty<LocalDate, StringQueryFieldParser<LocalDate>> DATE = () -> new StringQueryFieldParser<>() {
        @Override
        public LocalDate coerce(String value) {
            return LocalDate.parse(value);
        }

        @Override
        public String decoerce(LocalDate value) {
            return value.toString();
        }
    };
    SingleDBFieldProperty<Instant, StringQueryFieldParser<Instant>> DATE_TIME = () -> new StringQueryFieldParser<>() {
        @Override
        public Instant coerce(String value) {
            return ZonedDateTime.parse(value).toInstant();
        }

        @Override
        public String decoerce(Instant value) {
            return value.toString();
        }
    };
    LocalDateTimeTypeBuilder LOCAL_DATE_TIME = new LocalDateTimeTypeBuilder();

    non-sealed interface NumberQueryFieldParser<T> extends QueryFieldParser<T> {
        /// converts the given BigDecimal value to target propertyParser T
        T coerce(BigDecimal value);

        /// Converts the value from the specialized propertyParser T back to BigDecimal
        /// Note that calling decoerce with a coerced value should produce the original result.
        BigDecimal decoerce(T value);
    }

    non-sealed interface StringQueryFieldParser<T> extends QueryFieldParser<T> {
        /// converts the given String value to target propertyParser T
        T coerce(String value);

        /// Converts the value from the specialized propertyParser T back to String.
        /// Note that calling decoerce with a coerced value should produce the original result.
        String decoerce(T value);
    }

    non-sealed interface BooleanQueryFieldParser<T> extends QueryFieldParser<T> {
        /// Converts the given boolean value to target propertyParser T
        T coerce(boolean value);

        /// converts the value from the specialized propertyParser T back to boolean
        /// Note that calling decoerce with a coerced value should produce the original result.
        boolean decoerce(T value);
    }

    class LocalDateTimeTypeBuilder {
        public SingleDBFieldProperty<LocalDateTime, StringQueryFieldParser<LocalDateTime>> atZone(ZoneId zoneId) {
            return () -> new StringQueryFieldParser<>() {
                @Override
                public LocalDateTime coerce(String value) {
                    return ZonedDateTime.parse(value).withZoneSameInstant(zoneId).toLocalDateTime();
                }

                @Override
                public String decoerce(LocalDateTime value) {
                    return value.atZone(zoneId).format(DateTimeFormatter.ISO_INSTANT);
                }
            };
        }
    }
}
