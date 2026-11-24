package com.heldertheking.resourcename;

import com.heldertheking.resourcename.ResourceNameTest.TestPetName;
import com.heldertheking.resourcename.ResourceNameTest.TestPoolName;
import com.google.api.pathtemplate.PathTemplate;
import com.google.api.pathtemplate.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceNamesTest {

    private static final PathTemplate POOL = PathTemplate.create("pools/{pool}");
    private static final PathTemplate PET = PathTemplate.create("pools/{pool}/pets/{pet}");

    static TestPoolName parsePool(String name) {
        return new TestPoolName(Long.parseLong(variables(POOL, name).get("pool")));
    }

    static TestPetName parsePet(String name) {
        var vars = variables(PET, name);
        return TestPetName.of(Long.parseLong(vars.get("pool")), Long.parseLong(vars.get("pet")));
    }

    private static java.util.Map<String, String> variables(PathTemplate template, String name) {
        var vars = template.match(name);
        if (vars == null) {
            throw new ValidationException("'%s' does not match %s", name, template.toRawString());
        }
        return vars;
    }

    @Test
    void first_matching_type_wins() {
        Name name = ResourceNames.match("pools/1")
                .when(ResourceNamesTest::parsePool)
                .when(_ -> {
                    throw new AssertionError("must not be called after a match");
                })
                .orElseThrow();

        assertThat(name).isEqualTo(new TestPoolName(1));
    }

    @Test
    void falls_through_to_the_second_type() {
        Name name = ResourceNames.match("pools/1/pets/2")
                .when(ResourceNamesTest::parsePool)
                .when(ResourceNamesTest::parsePet)
                .orElseThrow();

        assertThat(name).isEqualTo(TestPetName.of(1, 2));
    }

    @Test
    void mapping_is_applied_to_the_parsed_name() {
        TestPoolName pool = ResourceNames.match("pools/1/pets/2", TestPoolName.class)
                .when(ResourceNamesTest::parsePool, Function.identity())
                .when(ResourceNamesTest::parsePet, TestPetName::getParent)
                .orElseThrow();

        assertThat(pool).isEqualTo(new TestPoolName(1));
    }

    @Test
    void result_is_empty_when_nothing_matches() {
        var result = ResourceNames.match("users/1", Long.class)
                .when(ResourceNamesTest::parsePool, TestPoolName::getPersistenceId)
                .when(ResourceNamesTest::parsePet, TestPetName::getPersistenceId)
                .result();

        assertThat(result).isEmpty();
    }

    @Test
    void result_is_present_when_a_type_matches() {
        var result = ResourceNames.match("pools/7", Long.class)
                .when(ResourceNamesTest::parsePool, TestPoolName::getPersistenceId)
                .result();

        assertThat(result).contains(7L);
    }

    @Test
    void orElseThrow_carries_every_cause_as_suppressed_when_nothing_matches() {
        var matcher = ResourceNames.match("pools/abc")
                .when(ResourceNamesTest::parsePool)
                .when(ResourceNamesTest::parsePet);

        assertThatThrownBy(matcher::orElseThrow)
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("pools/abc")
                .hasMessageContaining("2 resource name types")
                .satisfies(e -> assertThat(e.getSuppressed())
                        .hasSize(2)
                        .allSatisfy(s -> assertThat(s).isInstanceOf(IllegalArgumentException.class)));
    }

    @Test
    void exception_in_a_mapping_is_not_swallowed() {
        var matcher = ResourceNames.match("pools/1", Long.class);

        assertThatThrownBy(() -> matcher
                .when(ResourceNamesTest::parsePool, _ -> {
                    throw new IllegalStateException("bug in mapping");
                })
                .when(ResourceNamesTest::parsePool, TestPoolName::getPersistenceId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bug in mapping");
    }

    @Test
    void other_exceptions_of_a_parser_propagate() {
        var matcher = ResourceNames.match("pools/1");

        assertThatThrownBy(() -> matcher.when(_ -> {
            throw new UnsupportedOperationException("not a parse failure");
        })).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void null_name_is_rejected() {
        assertThatThrownBy(() -> ResourceNames.match(null)).isInstanceOf(NullPointerException.class);
    }
}
