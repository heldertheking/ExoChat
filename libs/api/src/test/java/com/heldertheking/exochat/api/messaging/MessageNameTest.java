package com.heldertheking.exochat.api.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.heldertheking.exochat.api.id.UuidV7;

class MessageNameTest {
    private static final UUID U0 = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01");
    private static final UUID U1 = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a02");

    @Test
    void of_should_formatAndExposePersistenceId() {
        var name = MessageName.of(ChannelName.of(U0), U1);

        assertThat(name).hasToString("channels/" + U0 + "/messages/" + U1);
        assertThat(name.getPersistenceId()).isEqualTo(U1);
        assertThat(name.isWildcard()).isFalse();
        assertThat(name.getTemplateVariables()).containsEntry("message", U1.toString());
    }

    @Test
    void wildcard_should_formatAsDashAndRejectPersistenceId() {
        var name = MessageName.wildcard(ChannelName.of(U0));

        assertThat(name).hasToString("channels/" + U0 + "/messages/-");
        assertThat(name.isWildcard()).isTrue();
        assertThatThrownBy(name::getPersistenceId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromName_should_roundTrip() {
        assertThat(MessageName.fromName("channels/" + U0 + "/messages/" + U1))
                .isEqualTo(MessageName.of(ChannelName.of(U0), U1));
        assertThat(MessageName.fromName("channels/" + U0 + "/messages/-"))
                .isEqualTo(MessageName.wildcard(ChannelName.of(U0)));
    }

    @Test
    void equals_and_hashCode_should_dependOnIdAndParent() {
        assertThat(MessageName.of(ChannelName.of(U0), U1))
                .isEqualTo(MessageName.of(ChannelName.of(U0), U1))
                .hasSameHashCodeAs(MessageName.of(ChannelName.of(U0), U1))
                .isNotEqualTo(MessageName.of(ChannelName.of(U0), UuidV7.next()))
                .isNotEqualTo(MessageName.wildcard(ChannelName.of(U0)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "messages",
                "messages/",
                "messages/alice",
                "messages/1",
                "other/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01",
                "messages/F47AC10B-58CC-4372-A567-0E02B2C3D479",
                "messages/f47ac10b-58cc-4372-a567-0e02b2c3d479",
                "/messages/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01"
            })
    void fromName_should_rejectOtherStrings(String name) {
        assertThatThrownBy(() -> MessageName.fromName(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_should_rejectNonV7Uuids() {
        assertThatThrownBy(() -> MessageName.of(ChannelName.of(U0), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_should_rejectNonWildcardChild_when_parentIsWildcard() {
        assertThatThrownBy(() -> MessageName.of(ChannelName.wildcard(), UuidV7.next()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
