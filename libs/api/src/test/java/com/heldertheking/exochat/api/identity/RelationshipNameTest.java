package com.heldertheking.exochat.api.identity;

import com.heldertheking.exochat.api.id.UuidV7;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationshipNameTest {
    private static final UUID U0 = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01");
    private static final UUID U1 = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a02");
    private static final UUID U2 = UUID.fromString("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a03");

    @Test
    void of_should_formatAndExposePersistenceId() {
        var name = RelationshipName.of(AccountName.of(UserName.of(U0), U1), U2);

        assertThat(name).hasToString("users/" + U0 + "/accounts/" + U1 + "/relationships/" + U2);
        assertThat(name.getPersistenceId()).isEqualTo(U2);
        assertThat(name.isWildcard()).isFalse();
        assertThat(name.getTemplateVariables()).containsEntry("relationship", U2.toString());
    }

    @Test
    void wildcard_should_formatAsDashAndRejectPersistenceId() {
        var name = RelationshipName.wildcard(AccountName.of(UserName.of(U0), U1));

        assertThat(name).hasToString("users/" + U0 + "/accounts/" + U1 + "/relationships/-");
        assertThat(name.isWildcard()).isTrue();
        assertThatThrownBy(name::getPersistenceId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromName_should_roundTrip() {
        assertThat(RelationshipName.fromName("users/" + U0 + "/accounts/" + U1 + "/relationships/" + U2)).isEqualTo(RelationshipName.of(AccountName.of(UserName.of(U0), U1), U2));
        assertThat(RelationshipName.fromName("users/" + U0 + "/accounts/" + U1 + "/relationships/-")).isEqualTo(RelationshipName.wildcard(AccountName.of(UserName.of(U0), U1)));
    }

    @Test
    void equals_and_hashCode_should_dependOnIdAndParent() {
        assertThat(RelationshipName.of(AccountName.of(UserName.of(U0), U1), U2))
                .isEqualTo(RelationshipName.of(AccountName.of(UserName.of(U0), U1), U2))
                .hasSameHashCodeAs(RelationshipName.of(AccountName.of(UserName.of(U0), U1), U2))
                .isNotEqualTo(RelationshipName.of(AccountName.of(UserName.of(U0), U1), UuidV7.next()))
                .isNotEqualTo(RelationshipName.wildcard(AccountName.of(UserName.of(U0), U1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "relationships", "relationships/", "relationships/alice", "relationships/1",
            "other/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01",
            "relationships/F47AC10B-58CC-4372-A567-0E02B2C3D479", "relationships/f47ac10b-58cc-4372-a567-0e02b2c3d479",
            "/relationships/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01"
    })
    void fromName_should_rejectOtherStrings(String name) {
        assertThatThrownBy(() -> RelationshipName.fromName(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_should_rejectNonV7Uuids() {
        assertThatThrownBy(() -> RelationshipName.of(AccountName.of(UserName.of(U0), U1), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_should_rejectNonWildcardChild_when_parentIsWildcard() {
        assertThatThrownBy(() -> RelationshipName.of(AccountName.wildcard(UserName.wildcard()), UuidV7.next()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
