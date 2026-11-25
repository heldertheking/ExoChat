package com.heldertheking.exochat.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.heldertheking.exochat.api.id.UuidV7;

class SessionNameTest {
    private static final UUID U0 = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01");
    private static final UUID U1 = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a02");

    @Test
    void of_should_formatAndExposePersistenceId() {
        var name = SessionName.of(UserName.of(U0), U1);

        assertThat(name).hasToString("users/" + U0 + "/sessions/" + U1);
        assertThat(name.getPersistenceId()).isEqualTo(U1);
        assertThat(name.isWildcard()).isFalse();
        assertThat(name.getTemplateVariables()).containsEntry("session", U1.toString());
    }

    @Test
    void wildcard_should_formatAsDashAndRejectPersistenceId() {
        var name = SessionName.wildcard(UserName.of(U0));

        assertThat(name).hasToString("users/" + U0 + "/sessions/-");
        assertThat(name.isWildcard()).isTrue();
        assertThatThrownBy(name::getPersistenceId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromName_should_roundTrip() {
        assertThat(SessionName.fromName("users/" + U0 + "/sessions/" + U1))
                .isEqualTo(SessionName.of(UserName.of(U0), U1));
        assertThat(SessionName.fromName("users/" + U0 + "/sessions/-"))
                .isEqualTo(SessionName.wildcard(UserName.of(U0)));
    }

    @Test
    void equals_and_hashCode_should_dependOnIdAndParent() {
        assertThat(SessionName.of(UserName.of(U0), U1))
                .isEqualTo(SessionName.of(UserName.of(U0), U1))
                .hasSameHashCodeAs(SessionName.of(UserName.of(U0), U1))
                .isNotEqualTo(SessionName.of(UserName.of(U0), UuidV7.next()))
                .isNotEqualTo(SessionName.wildcard(UserName.of(U0)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "sessions",
                "sessions/",
                "sessions/alice",
                "sessions/1",
                "other/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01",
                "sessions/F47AC10B-58CC-4372-A567-0E02B2C3D479",
                "sessions/f47ac10b-58cc-4372-a567-0e02b2c3d479",
                "/sessions/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01"
            })
    void fromName_should_rejectOtherStrings(String name) {
        assertThatThrownBy(() -> SessionName.fromName(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_should_rejectNonV7Uuids() {
        assertThatThrownBy(() -> SessionName.of(UserName.of(U0), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_should_rejectNonWildcardChild_when_parentIsWildcard() {
        assertThatThrownBy(() -> SessionName.of(UserName.wildcard(), UuidV7.next()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
