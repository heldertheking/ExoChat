package com.heldertheking.exochat.api.messaging;

import com.heldertheking.exochat.api.id.UuidV7;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelNameTest {
    private static final UUID U0 = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01");

    @Test
    void of_should_formatAndExposePersistenceId() {
        var name = ChannelName.of(U0);

        assertThat(name).hasToString("channels/" + U0);
        assertThat(name.getPersistenceId()).isEqualTo(U0);
        assertThat(name.isWildcard()).isFalse();
        assertThat(name.getTemplateVariables()).containsEntry("channel", U0.toString());
    }

    @Test
    void wildcard_should_formatAsDashAndRejectPersistenceId() {
        var name = ChannelName.wildcard();

        assertThat(name).hasToString("channels/-");
        assertThat(name.isWildcard()).isTrue();
        assertThatThrownBy(name::getPersistenceId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromName_should_roundTrip() {
        assertThat(ChannelName.fromName("channels/" + U0)).isEqualTo(ChannelName.of(U0));
        assertThat(ChannelName.fromName("channels/-")).isEqualTo(ChannelName.wildcard());
    }

    @Test
    void equals_and_hashCode_should_dependOnIdAndParent() {
        assertThat(ChannelName.of(U0))
                .isEqualTo(ChannelName.of(U0))
                .hasSameHashCodeAs(ChannelName.of(U0))
                .isNotEqualTo(ChannelName.of(UuidV7.next()))
                .isNotEqualTo(ChannelName.wildcard());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "channels", "channels/", "channels/alice", "channels/1",
            "other/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01",
            "channels/F47AC10B-58CC-4372-A567-0E02B2C3D479", "channels/f47ac10b-58cc-4372-a567-0e02b2c3d479",
            "/channels/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01"
    })
    void fromName_should_rejectOtherStrings(String name) {
        assertThatThrownBy(() -> ChannelName.fromName(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_should_rejectNonV7Uuids() {
        assertThatThrownBy(() -> ChannelName.of(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
