package com.heldertheking.exochat.app.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.heldertheking.exochat.common.v1alpha1.RuleSetting;
import com.heldertheking.exochat.identity.v1alpha1.Account;

/// The enums of the permission model are also protobuf enums; adding a value to one side only must fail a test, not
/// show up as a runtime surprise.
class ProtoEnumSyncTest {

    private static java.util.Set<String> protoNames(
            com.google.protobuf.Descriptors.EnumDescriptor descriptor, String prefix) {
        return descriptor.getValues().stream()
                .map(value -> value.getName())
                .filter(name -> !name.endsWith("_UNSPECIFIED"))
                .map(name -> name.substring(prefix.length()))
                .collect(Collectors.toSet());
    }

    @Test
    void rule_should_matchTheProtoEnum() {
        var proto = protoNames(com.heldertheking.exochat.common.v1alpha1.Rule.getDescriptor(), "RULE_");

        assertThat(Rule.all().stream().map(Rule::name)).containsExactlyInAnyOrderElementsOf(proto);
    }

    @Test
    void ruleState_should_matchTheProtoEnum() {
        var proto = protoNames(com.heldertheking.exochat.common.v1alpha1.RuleState.getDescriptor(), "RULE_STATE_");

        assertThat(Arrays.stream(RuleState.values()).map(Enum::name)).containsExactlyInAnyOrderElementsOf(proto);
    }

    @Test
    void standing_should_matchTheProtoEnum() {
        var proto = protoNames(Account.AccountStanding.getDescriptor(), "ACCOUNT_STANDING_");

        assertThat(Arrays.stream(Standing.values()).map(Enum::name)).containsExactlyInAnyOrderElementsOf(proto);
    }

    @Test
    void ruleSetting_should_beUsableInTheMessages() {
        assertThat(RuleSetting.getDescriptor().findFieldByName("rule")).isNotNull();
        assertThat(RuleSetting.getDescriptor().findFieldByName("state")).isNotNull();
    }
}
