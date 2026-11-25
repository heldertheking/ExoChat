package com.heldertheking.quela.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.heldertheking.quela.pagination.PaginationTokenSerializer.Token;

class Base64PaginationTokenSerializerTest {
    private final Base64PaginationTokenSerializer serializer = new Base64PaginationTokenSerializer();

    @Test
    void roundTrips_values_including_null_empty_and_unicode() {
        var token = new Token(-1234567890123L, Arrays.asList("a", null, "", "ünï €", "x".repeat(5000)));

        var serialized = serializer.serialize(token);

        assertThat(serialized).matches("[A-Za-z0-9_-]+");
        assertThat(serializer.deserialize(serialized)).isEqualTo(token);
    }

    @Test
    void roundTrips_noValues() {
        var token = new Token(7L, List.of());

        assertThat(serializer.deserialize(serializer.serialize(token))).isEqualTo(token);
    }

    @Test
    void rejects_malformedTokens() {
        assertThatThrownBy(() -> serializer.deserialize("%%%")).isInstanceOf(InvalidPageTokenException.class);
        assertThatThrownBy(() -> serializer.deserialize("AAAA")).isInstanceOf(InvalidPageTokenException.class);
        assertThatThrownBy(() -> serializer.deserialize("")).isInstanceOf(InvalidPageTokenException.class);
        var valid = serializer.serialize(new Token(1L, List.of("a")));
        assertThatThrownBy(() -> serializer.deserialize(valid + "AAAA")).isInstanceOf(InvalidPageTokenException.class);
    }
}
