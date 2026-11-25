package com.heldertheking.exochat.api.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.heldertheking.exochat.api.id.UuidV7;

class ServerNameTest {
    private static final UUID U0 = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01");

    @Test
    void of_should_formatAndExposePersistenceId() {
        var name = ServerName.of(U0);

        assertThat(name).hasToString("servers/" + U0);
        assertThat(name.getPersistenceId()).isEqualTo(U0);
        assertThat(name.isWildcard()).isFalse();
        assertThat(name.getTemplateVariables()).containsEntry("server", U0.toString());
    }

    @Test
    void wildcard_should_formatAsDashAndRejectPersistenceId() {
        var name = ServerName.wildcard();

        assertThat(name).hasToString("servers/-");
        assertThat(name.isWildcard()).isTrue();
        assertThatThrownBy(name::getPersistenceId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromName_should_roundTrip() {
        assertThat(ServerName.fromName("servers/" + U0)).isEqualTo(ServerName.of(U0));
        assertThat(ServerName.fromName("servers/-")).isEqualTo(ServerName.wildcard());
    }

    @Test
    void equals_and_hashCode_should_dependOnIdAndParent() {
        assertThat(ServerName.of(U0))
                .isEqualTo(ServerName.of(U0))
                .hasSameHashCodeAs(ServerName.of(U0))
                .isNotEqualTo(ServerName.of(UuidV7.next()))
                .isNotEqualTo(ServerName.wildcard());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "servers",
                "servers/",
                "servers/alice",
                "servers/1",
                "other/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01",
                "servers/F47AC10B-58CC-4372-A567-0E02B2C3D479",
                "servers/f47ac10b-58cc-4372-a567-0e02b2c3d479",
                "/servers/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01"
            })
    void fromName_should_rejectOtherStrings(String name) {
        assertThatThrownBy(() -> ServerName.fromName(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_should_rejectNonV7Uuids() {
        assertThatThrownBy(() -> ServerName.of(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }
}
