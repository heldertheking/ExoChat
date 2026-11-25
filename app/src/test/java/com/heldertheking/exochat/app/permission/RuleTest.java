package com.heldertheking.exochat.app.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuleTest {

    @Test
    void all_should_listTheDeclaredRulesWithoutDuplicates() {
        assertThat(Rule.all()).contains(Rule.SEND_MESSAGES, Rule.MANAGE_ROLES, Rule.CREATE_ACCOUNT);
        assertThat(Rule.all().stream().map(Rule::name)).doesNotHaveDuplicates();
    }

    @Test
    void find_should_returnKnownRulesOnly() {
        assertThat(Rule.find("SEND_MESSAGES")).contains(Rule.SEND_MESSAGES);
        assertThat(Rule.find("A_RULE_FROM_THE_FUTURE")).isEmpty();
        assertThat(Rule.find("send_messages")).as("names are case sensitive").isEmpty();
    }

    @Test
    void defaults_should_allowMostRulesAndDenyManagementRules() {
        assertThat(Rule.SEND_MESSAGES.allowedByDefault()).isTrue();
        assertThat(Rule.MANAGE_SERVER.allowedByDefault()).isFalse();
    }

    @Test
    void aRuleShouldBeAValueWithAName() {
        assertThat(new Rule("SEND_MESSAGES", true))
                .isEqualTo(Rule.SEND_MESSAGES)
                .hasToString("SEND_MESSAGES");
        assertThatThrownBy(() -> new Rule(" ", true)).isInstanceOf(IllegalArgumentException.class);
    }
}
