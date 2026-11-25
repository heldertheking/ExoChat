package com.heldertheking.exochat.api.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.heldertheking.exochat.api.community.ServerName;
import com.heldertheking.exochat.api.id.UuidV7;

class ServerChannelNameTest {
    private static final UUID U0 = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01");
    private static final UUID U1 = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a02");

    @Test
    void of_should_formatAndExposePersistenceId() {
        var name = ServerChannelName.of(ServerName.of(U0), U1);

        assertThat(name).hasToString("servers/" + U0 + "/channels/" + U1);
        assertThat(name.getPersistenceId()).isEqualTo(U1);
        assertThat(name.isWildcard()).isFalse();
    }

    @Test
    void wildcard_should_formatAsDashAndRejectPersistenceId() {
        var name = ServerChannelName.wildcard(ServerName.of(U0));

        assertThat(name).hasToString("servers/" + U0 + "/channels/-");
        assertThat(name.isWildcard()).isTrue();
        assertThatThrownBy(name::getPersistenceId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromName_should_roundTrip() {
        assertThat(ServerChannelName.fromName("servers/" + U0 + "/channels/" + U1))
                .isEqualTo(ServerChannelName.of(ServerName.of(U0), U1));
        assertThat(ServerChannelName.fromName("servers/-/channels/-"))
                .isEqualTo(ServerChannelName.wildcard(ServerName.wildcard()));
    }

    @Test
    void toChannelName_should_pointAtTheSameChannel() {
        assertThat(ServerChannelName.of(ServerName.of(U0), U1).toChannelName()).isEqualTo(ChannelName.of(U1));
        assertThat(ServerChannelName.wildcard(ServerName.of(U0)).toChannelName())
                .isEqualTo(ChannelName.wildcard());
    }

    @Test
    void channelName_should_convertBothWays() {
        var server = ServerName.of(U0);

        assertThat(ChannelName.of(U1).inServer(server)).isEqualTo(ServerChannelName.of(server, U1));
        assertThat(ChannelName.wildcard().inServer(server)).isEqualTo(ServerChannelName.wildcard(server));
        assertThat(ChannelName.fromAnyName("channels/" + U1)).isEqualTo(ChannelName.of(U1));
        assertThat(ChannelName.fromAnyName("servers/" + U0 + "/channels/" + U1)).isEqualTo(ChannelName.of(U1));
    }

    @Test
    void equals_and_hashCode_should_dependOnIdAndParent() {
        assertThat(ServerChannelName.of(ServerName.of(U0), U1))
                .isEqualTo(ServerChannelName.of(ServerName.of(U0), U1))
                .hasSameHashCodeAs(ServerChannelName.of(ServerName.of(U0), U1))
                .isNotEqualTo(ServerChannelName.of(ServerName.of(U0), UuidV7.next()))
                .isNotEqualTo(ServerChannelName.wildcard(ServerName.of(U0)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "channels/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01",
                "servers/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01/channels/alice",
                "servers/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01/channels/",
                "servers/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01/channels/0190A1B2-C3D4-7E5F-8A6B-7C8D9E0F1A02",
            })
    void fromName_should_rejectInvalidNames(String name) {
        assertThatThrownBy(() -> ServerChannelName.fromName(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_should_rejectNonV7Id() {
        assertThatThrownBy(() -> ServerChannelName.of(ServerName.of(U0), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
