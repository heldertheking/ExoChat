package com.heldertheking.quela;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidFieldParserTest {
    private final QueryFieldParser.StringQueryFieldParser<UUID> parser = QueryFieldParser.UUID.getQueryPropertyParser();

    @Test
    void coerceAndDecoerce_should_roundTrip() {
        var id = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01");

        assertThat(parser.coerce(parser.decoerce(id))).isEqualTo(id);
    }

    @Test
    void coerce_should_rejectNonUuids() {
        assertThatThrownBy(() -> parser.coerce("nope")).isInstanceOf(FieldParseException.class);
    }
}
