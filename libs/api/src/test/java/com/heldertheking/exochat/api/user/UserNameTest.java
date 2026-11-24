package com.heldertheking.exochat.api.user;

import com.google.api.pathtemplate.ValidationException;
import com.heldertheking.exochat.api.id.UuidV7;
import com.heldertheking.resourcename.Name;
import com.heldertheking.resourcename.ResourceNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserNameTest {
    private static final UUID ID = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b");

    @Test
    void of_should_formatAndExposePersistenceId() {
        var name = UserName.of(ID);

        assertThat(name).hasToString("users/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b");
        assertThat(name.getPersistenceId()).isEqualTo(ID);
        assertThat(name.isWildcard()).isFalse();
        assertThat(name.getTemplateVariables()).isEqualTo(Map.of("user", ID.toString()));
    }

    @Test
    void wildcard_should_formatAsDashAndRejectPersistenceId() {
        var name = UserName.wildcard();

        assertThat(name).hasToString("users/-");
        assertThat(name.isWildcard()).isTrue();
        assertThatThrownBy(name::getPersistenceId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromName_should_roundTrip() {
        var generated = UuidV7.next();

        assertThat(UserName.fromName("users/" + ID)).isEqualTo(UserName.of(ID));
        assertThat(UserName.fromName("users/-")).isEqualTo(UserName.wildcard());
        assertThat(UserName.fromName(UserName.of(generated).toString()).getPersistenceId()).isEqualTo(generated);
    }

    @Test
    void equals_and_hashCode_should_dependOnId() {
        assertThat(UserName.of(ID))
                .isEqualTo(UserName.of(ID))
                .hasSameHashCodeAs(UserName.of(ID))
                .isNotEqualTo(UserName.of(UuidV7.next()))
                .isNotEqualTo(UserName.wildcard());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "users", "users/", "users/alice", "users/1",
            "users/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b/accounts/1",
            "pools/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b",
            "/users/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b",
            "users/0190A1B2-C3D4-7E5F-8A6B-7C8D9E0F1A2B",   // uppercase is not canonical
            "users/6ba7b810-9dad-11d1-80b4-00c04fd430c8",   // version 1
            "users/f47ac10b-58cc-4372-a567-0e02b2c3d479"    // version 4
    })
    void fromName_should_rejectOtherStrings(String name) {
        assertThatThrownBy(() -> UserName.fromName(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_should_rejectNonV7Uuids() {
        assertThatThrownBy(() -> UserName.of(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldWorkWithResourceNamesMatcher() {
        Name name = ResourceNames.match("users/" + ID).when(UserName::fromName).orElseThrow();
        assertThat(name).isEqualTo(UserName.of(ID));

        assertThatThrownBy(() -> ResourceNames.match("pools/1").when(UserName::fromName).orElseThrow())
                .isInstanceOf(ValidationException.class);
    }
}
