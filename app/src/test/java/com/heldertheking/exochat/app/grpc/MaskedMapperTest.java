package com.heldertheking.exochat.app.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.google.protobuf.FieldMask;

class MaskedMapperTest {
    private final AtomicReference<String> name = new AtomicReference<>();
    private final AtomicReference<Integer> value = new AtomicReference<>();

    private static FieldMask mask(String... paths) {
        return FieldMask.newBuilder().addAllPaths(java.util.List.of(paths)).build();
    }

    @Test
    void setIfMasked_should_setOnlyTheNamedProperties() {
        MaskedMapper.forMask(mask("name"))
                .setIfMasked("name", "Orange", name::set)
                .setIfMasked("value", 50, value::set);

        assertThat(name.get()).isEqualTo("Orange");
        assertThat(value.get()).isNull();
    }

    @Test
    void setIfMasked_should_setEverything_withoutAMaskOrWithTheWildcard() {
        MaskedMapper.forNoExplicitMask().setIfMasked("name", "A", name::set).setIfMasked("value", 1, value::set);
        assertThat(name.get()).isEqualTo("A");
        assertThat(value.get()).isEqualTo(1);

        name.set(null);
        value.set(null);
        MaskedMapper.forMask(null).setIfMasked("name", "B", name::set);
        MaskedMapper.forMask(mask("*")).setIfMasked("value", 2, value::set);
        assertThat(name.get()).isEqualTo("B");
        assertThat(value.get()).isEqualTo(2);
    }

    @Test
    void setIfMaskedAndPresent_should_notReadTheValueOfAnAbsentProperty() {
        MaskedMapper.forMask(mask("name"))
                .setIfMaskedAndPresent(
                        "name",
                        false,
                        () -> {
                            throw new AssertionError("must not be read");
                        },
                        name::set);

        assertThat(name.get()).isNull();
    }

    @Test
    void throwOnUnmappedProperty_should_nameThePathsNoPropertyClaimed() {
        var mapper = MaskedMapper.forMask(mask("name", "create_time", "other")).setIfMasked("name", "x", name::set);

        assertThatThrownBy(mapper::throwOnUnmappedProperty)
                .isInstanceOf(UnmappedPropertyException.class)
                .hasMessageContaining("create_time")
                .hasMessageContaining("other")
                .extracting("unmappedPaths")
                .isEqualTo(Set.of("create_time", "other"));
    }

    @Test
    void throwOnUnmappedProperty_should_acceptMappedPathsTheWildcardAndNoMask() {
        assertThatCode(MaskedMapper.forMask(mask("name")).setIfMasked("name", "x", name::set)::throwOnUnmappedProperty)
                .doesNotThrowAnyException();
        assertThatCode(MaskedMapper.forMask(mask("*")).setIfMasked("name", "x", name::set)::throwOnUnmappedProperty)
                .doesNotThrowAnyException();
        assertThatCode(MaskedMapper.forNoExplicitMask()::throwOnUnmappedProperty)
                .doesNotThrowAnyException();
    }
}
