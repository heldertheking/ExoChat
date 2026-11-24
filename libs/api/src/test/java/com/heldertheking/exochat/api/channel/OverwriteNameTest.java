package com.heldertheking.exochat.api.channel;

import com.heldertheking.exochat.api.id.UuidV7;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverwriteNameTest {
    private static final UUID U0 = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01");
    private static final UUID U1 = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a02");

    @Test
    void of_should_formatAndExposePersistenceId() {
        var name = OverwriteName.of(ChannelName.of(U0), U1);

        assertThat(name).hasToString("channels/" + U0 + "/overwrites/" + U1);
        assertThat(name.getPersistenceId()).isEqualTo(U1);
        assertThat(name.isWildcard()).isFalse();
        assertThat(name.getTemplateVariables()).containsEntry("overwrite", U1.toString());
    }

    @Test
    void wildcard_should_formatAsDashAndRejectPersistenceId() {
        var name = OverwriteName.wildcard(ChannelName.of(U0));

        assertThat(name).hasToString("channels/" + U0 + "/overwrites/-");
        assertThat(name.isWildcard()).isTrue();
        assertThatThrownBy(name::getPersistenceId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromName_should_roundTrip() {
        assertThat(OverwriteName.fromName("channels/" + U0 + "/overwrites/" + U1)).isEqualTo(OverwriteName.of(ChannelName.of(U0), U1));
        assertThat(OverwriteName.fromName("channels/" + U0 + "/overwrites/-")).isEqualTo(OverwriteName.wildcard(ChannelName.of(U0)));
    }

    @Test
    void equals_and_hashCode_should_dependOnIdAndParent() {
        assertThat(OverwriteName.of(ChannelName.of(U0), U1))
                .isEqualTo(OverwriteName.of(ChannelName.of(U0), U1))
                .hasSameHashCodeAs(OverwriteName.of(ChannelName.of(U0), U1))
                .isNotEqualTo(OverwriteName.of(ChannelName.of(U0), UuidV7.next()))
                .isNotEqualTo(OverwriteName.wildcard(ChannelName.of(U0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "overwrites", "overwrites/", "overwrites/alice", "overwrites/1",
            "other/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01",
            "overwrites/F47AC10B-58CC-4372-A567-0E02B2C3D479", "overwrites/f47ac10b-58cc-4372-a567-0e02b2c3d479",
            "/overwrites/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01"
    })
    void fromName_should_rejectOtherStrings(String name) {
        assertThatThrownBy(() -> OverwriteName.fromName(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_should_rejectNonV7Uuids() {
        assertThatThrownBy(() -> OverwriteName.of(ChannelName.of(U0), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_should_rejectNonWildcardChild_when_parentIsWildcard() {
        assertThatThrownBy(() -> OverwriteName.of(ChannelName.wildcard(), UuidV7.next()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
