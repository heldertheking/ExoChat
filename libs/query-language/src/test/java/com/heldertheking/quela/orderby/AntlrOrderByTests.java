package com.heldertheking.quela.orderby;

import static com.heldertheking.quela.QueryFieldParser.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.heldertheking.quela.FieldResolverImpl;

public class AntlrOrderByTests {
    OrderByParser parser = AntlrOrderByParser.newBuilder()
            .withDiscriminator(LONG, DSL.field("id", Long.class))
            .withFieldResolver(FieldResolverImpl.newBuilder()
                    .addSortableField(STRING, "some_field", DSL.field("some_field", String.class))
                    .addSortableField(STRING, "complex.field", DSL.field("complex.field", String.class))
                    .addSortableField(LONG, "a", DSL.field("x", Long.class))
                    .addSortableField(DOUBLE, "b", DSL.field("y", Double.class))
                    .addSortableField(INTEGER, "c", DSL.field("z", Integer.class))
                    .addField(STRING, "non_sortable_field", DSL.field("non_sortable_field", String.class))
                    .build())
            .build();

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "some_field                     | some_field asc,id asc",
                "some_field ASC                 | some_field asc,id asc",
                "some_field asc                 | some_field asc,id asc",
                "some_field DESC                | some_field desc,id desc",
                "some_field       desc          | some_field desc,id desc",
                "complex.field asc              | complex.field asc,id asc",
                "a                              | x asc,id asc",
                "a,     b desc,     c asc       | x asc,y desc,z asc,id asc",
                "a asc, b desc                  | x asc,y desc,id desc",
            })
    void expect_order_by_to_be_parsed(String orderBy, String expectedStr) {
        var expected = expectedStr.split(",");
        var condition = parser.parse(orderBy);
        assertThat(condition.sortFields()).map(Object::toString).containsExactly(expected);
    }

    @Test
    void expect_FieldResolutionException_if_field_is_not_defined() {
        assertThatThrownBy(() -> parser.parse("unknown_field"))
                .isInstanceOf(OrderByParseException.class)
                .hasMessage("Error parsing order by clause: Unknown field `unknown_field` found in query");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "some_ field asc",
                "some_field desc, some_field desc, ",
                "some_field desc, some_field desc, ",
            })
    void expect_OrderByParseException_if_query_is_invalid(String orderBy) {
        assertThatThrownBy(() -> parser.parse(orderBy))
                .isInstanceOf(OrderByParseException.class)
                .hasMessageStartingWith("Error parsing order by clause");
    }

    @Test
    void expect_empty_orderBy_condition_to_return_discriminatory_field_only() {
        var condition = parser.parse("");
        assertThat(condition.sortFields()).map(Object::toString).containsExactly("id asc");
    }

    @Test
    void expect_parser_to_ignore_non_sortable_field_definitions() {
        assertThatThrownBy(() -> parser.parse("non_sortable_field asc"))
                .isInstanceOf(OrderByParseException.class)
                .hasMessageStartingWith(
                        "Error parsing order by clause: field `non_sortable_field` is not sortable and must not be used in an order_by clause");
    }
}
