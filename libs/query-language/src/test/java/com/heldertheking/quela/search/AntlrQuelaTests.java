package com.heldertheking.quela.search;

import com.heldertheking.quela.BaseConditionFactory;
import com.heldertheking.quela.FieldResolver;
import com.heldertheking.quela.FieldResolverImpl;
import org.assertj.core.api.Assertions;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.heldertheking.quela.QueryFieldParser.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jooq.impl.DSL.using;

public class AntlrQuelaTests {
    DSLContext dsl = using(SQLDialect.MARIADB);

    @Nested
    class Working_with_a_standard_parser {
        FieldResolver fieldResolver = FieldResolverImpl.newBuilder()
                .addField(STRING, "comment", DSL.field("comment", String.class))
                .addField(LONG, "some_count", DSL.field("some_count_field", Long.class))
                .addField(INTEGER, "a_number", DSL.field("a_number", Integer.class))
                .addField(BOOLEAN, "is_ok", DSL.field("bool_prop", Boolean.class))
                .addField(DOUBLE, "avg", DSL.field("average", Double.class))
                .addField(DATE, "simple_date", DSL.field("simple_date", LocalDate.class))
                .addField(DATE_TIME, "simple_instant", DSL.field("simple_instant", Instant.class))
                .addField(LOCAL_DATE_TIME.atZone(ZoneId.of("Europe/Zurich")), "local_datetime", DSL.field("local_datetime", LocalDateTime.class))
                .addField(STRING, "complex_field", DSL.field(dsl.select(DSL.field("a", String.class)).from("b")))
                .addField(STRING, "some.sub.field", DSL.field("some_sub_field", String.class))
                .build();
        SearchParser parser = AntlrSearchParser.newBuilder()
                .withFieldResolver(fieldResolver)
                .build();

        @Test
        void expect_noCondition_on_empty_string() {
            assertThat(parser.parse("")).isEqualTo(DSL.noCondition());
        }

        @Test
        void expect_noCondition_on_null_query() {
            assertThat(parser.parse(null)).isEqualTo(DSL.noCondition());
        }

        @ParameterizedTest
        @CsvSource({
                "comment='some comment', comment = ?, some comment",
                "some_count > 10, some_count_field > ?, 10",
                "a_number < 15, a_number < ?, 15",
                "is_ok = true, bool_prop = ?, true",
                "avg >= 10.5, average >= ?, 10.5",
                "avg <= 10, average <= ?, 10.0",
                "a_number != 123456789, a_number <> ?, 123456789",
                "complex_field = 'some_value', (select a from b) = ?, some_value",
                "comment = 'very-!important 😉', comment = ?, very-!important 😉",
                "some.sub.field = 'some value', some_sub_field = ?, some value",
                "comment = 'hello world' and a_number = 5 or a_number > 30, (comment = ? and (a_number = ? or a_number > ?)), hello world;5;30",
                "(comment = 'hello world' and a_number = 5) or a_number > 30, ((comment = ? and a_number = ?) or a_number > ?), hello world;5;30",
                "comment = 'hello*', comment like ?, hello%",
                "comment = '*some value*', comment like ?, %some value%",
                "comment = '\"or 1=1', comment = ?, \"or 1=1",
                "comment = null, comment is null, ",
                "comment != null, comment is not null, ",
                "comment = null and not a_number = 5, (comment is null and not (a_number = ?)), 5",
                "simple_date = '2024-10-11', simple_date = ?, 2024-10-11",
                "simple_instant = '2024-10-11T14:35:19Z', simple_instant = ?, 2024-10-11T14:35:19Z",
                "simple_instant >= '2024-10-11T12:17:00+01:00', simple_instant >= ?, 2024-10-11T11:17:00Z",
                "local_datetime = '2024-10-11T14:35:19-01:00', local_datetime = ?, 2024-10-11T17:35:19",
                "'some_count in [1, 5, 30]', 'some_count_field in (?, ?, ?)', 1;5;30",
        })
        void parse_should_return_translated_condition_if_query_is_well_formed(String query, String expectedCondition, String bindValues) throws QueryParseException {
            var condition = parser.parse(query);
            assertThat(dsl.render(condition)).isEqualTo(expectedCondition);
            if (bindValues == null) {
                assertThat(dsl.extractBindValues(condition)).isEmpty();
            } else {
                var expectedBindValues = bindValues.split(";");
                assertThat(dsl.extractBindValues(condition)).map(Object::toString).containsExactly(expectedBindValues);
            }
        }

        @ParameterizedTest
        @CsvSource({
                "some random invalid query, failed to parse search query at position 5: no viable alternative at input 'somerandom'",
                "inexisting.field = 'abc', failed to resolve field 'inexisting.field' in search query",
                "simple_date > '20', failed to coerce STRING value '20' for field 'simple_date'",
                "simple_instant = '2024-10-11T12:17:00', failed to coerce STRING value '2024-10-11T12:17:00' for field 'simple_instant'",
                "local_datetime = '2024-10-11T12:18:00', failed to coerce STRING value '2024-10-11T12:18:00' for field 'local_datetime'",
                "local_datetime = 2024, failed to parse search query: literal '2024' is expected to be a string when comparing with field 'local_datetime'",
                "local_datetime = true, failed to parse search query: literal 'true' is expected to be a string when comparing with field 'local_datetime'",
                "is_ok = 'a', failed to parse search query: literal 'a' is expected to be a boolean when comparing with field 'is_ok'",
                "avg = true, failed to parse search query: literal 'true' is expected to be a number when comparing with field 'avg'",
                "a_number > 10.3, failed to coerce NUMBER value '10.3' for field 'a_number'",
                "some_count <= 1234.56, failed to coerce NUMBER value '1234.56' for field 'some_count'",
        })
        void parse_should_throw_exception_if_query_is_invalid(String query, String errorMessage) throws QueryParseException {
            Assertions.setMaxStackTraceElementsDisplayed(15);
            assertThatThrownBy(() -> parser.parse(query))
                    .isInstanceOf(QueryParseException.class)
                    .hasMessageStartingWith(errorMessage)
                    .extracting(throwable -> ((QueryParseException) throwable).getQuery())
                    .isEqualTo(query);
        }
    }

    @Nested
    class Working_with_specialised_types {
        FieldResolver fieldResolver = FieldResolverImpl.newBuilder()
                .addField(new PairQueryPropertyParser(), "my_pair_field", new BaseConditionFactory<Pair>() {
                    @Override
                    public Condition eq(Pair value) {
                        return DSL.row(DSL.field("a_db_field", Integer.class), DSL.field("b_db_field", Integer.class))
                                .eq(value.a, value.b);
                    }
                })
                .build();
        SearchParser searchParser = AntlrSearchParser.newBuilder().withFieldResolver(fieldResolver).build();

        @Test
        void expect_specialised_type_to_be_coerced_into_target_value() {
            var condition = searchParser.parse("my_pair_field = '10,20'");
            assertThat(dsl.render(condition)).isEqualTo("(a_db_field, b_db_field) = (?, ?)");
            assertThat(dsl.extractBindValues(condition)).containsExactly(10, 20);
        }

        @Test
        void expect_QueryParseException_if_an_unsupported_operator_is_used() {
            assertThatThrownBy(() -> searchParser.parse("my_pair_field > '10,20'"))
                    .isInstanceOf(QueryParseException.class)
                    .hasMessage("operator '>' is not supported on field 'my_pair_field'");
        }

        private record Pair(int a, int b) {
        }

        private static class PairQueryPropertyParser implements StringQueryFieldParser<Pair> {
            @Override
            public Pair coerce(String value) {
                var values = value.split(",");
                if (values.length != 2) {
                    throw new IllegalArgumentException("value '%s' is not a valid pair field propertyParser".formatted(value));
                }
                return new Pair(Integer.parseInt(values[0]), Integer.parseInt(values[1]));
            }

            @Override
            public String decoerce(Pair value) {
                return "%s,%s".formatted(value.a, value.b);
            }
        }
    }
}
