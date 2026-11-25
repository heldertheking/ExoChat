package com.heldertheking.exochat.app.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.heldertheking.exochat.app.permission.PermissionInput.RoleRules;

class PermissionResolverTest {

    private static PermissionInput platform(Standing standing, RuleSet overrides) {
        return PermissionInput.platform(standing, overrides);
    }

    private static PermissionInput server(boolean owner, RoleRules... roles) {
        return new PermissionInput(Standing.MEMBER, RuleSet.empty(), owner, List.of(roles), List.of(), null);
    }

    private static RoleRules role(int position, RuleSet rules) {
        return new RoleRules(position, rules);
    }

    private static boolean allowed(Rule rule, PermissionInput input) {
        return PermissionResolver.isAllowed(rule, input);
    }

    @Nested
    class Defaults {
        @Test
        void should_allowOrDenyAccordingToTheRuleWhenNothingDecides() {
            var input = platform(Standing.MEMBER, RuleSet.empty());

            assertThat(allowed(Rule.SEND_MESSAGES, input)).isTrue();
            assertThat(allowed(Rule.CREATE_SERVER, input)).isTrue();
            assertThat(allowed(Rule.MANAGE_ROLES, input)).isFalse();
            assertThat(allowed(Rule.MANAGE_MESSAGES, input)).isFalse();
        }
    }

    @Nested
    class PlatformLayer {
        @Test
        void restricted_should_denyTheGeneralRuleSetOnly() {
            var input = platform(Standing.RESTRICTED, RuleSet.empty());

            assertThat(allowed(Rule.CREATE_SERVER, input)).isFalse();
            assertThat(allowed(Rule.CREATE_INVITE, input)).isFalse();
            assertThat(allowed(Rule.SEND_FRIEND_REQUESTS, input)).isFalse();
            assertThat(allowed(Rule.SEND_MESSAGES, input)).isTrue();
        }

        @Test
        void suspended_should_denyEverythingEvenWhatIsAllowedByDefault() {
            var input = platform(Standing.SUSPENDED, RuleSet.empty());

            for (var rule : Rule.all()) {
                assertThat(allowed(rule, input)).as(rule.name()).isFalse();
            }
        }

        @Test
        void anAccountOverrideShouldRefineTheStanding() {
            var lifted = platform(Standing.RESTRICTED, RuleSet.allowing(Rule.CREATE_SERVER));
            var tightened = platform(Standing.MEMBER, RuleSet.denying(Rule.SEND_MESSAGES));

            assertThat(allowed(Rule.CREATE_SERVER, lifted))
                    .as("platform allow lifts the denial of the standing")
                    .isTrue();
            assertThat(allowed(Rule.CREATE_INVITE, lifted))
                    .as("other denials stay")
                    .isFalse();
            assertThat(allowed(Rule.SEND_MESSAGES, tightened)).isFalse();
            assertThat(allowed(Rule.CONNECT, tightened)).isTrue();
        }

        @Test
        void anAllowShouldNotGrantAManagementRuleByItself() {
            var input = platform(Standing.MEMBER, RuleSet.allowing(Rule.MANAGE_SERVER));

            assertThat(allowed(Rule.MANAGE_SERVER, input)).isFalse();
        }

        @Test
        void aPlatformDenialShouldBeACeilingForEveryLowerLayer() {
            var input = new PermissionInput(
                    Standing.MEMBER,
                    RuleSet.denying(Rule.SEND_MESSAGES, Rule.MANAGE_SERVER),
                    true,
                    List.of(role(10, RuleSet.allowing(Rule.SEND_MESSAGES, Rule.MANAGE_SERVER))),
                    List.of(role(10, RuleSet.allowing(Rule.SEND_MESSAGES))),
                    RuleSet.allowing(Rule.SEND_MESSAGES));

            assertThat(allowed(Rule.SEND_MESSAGES, input))
                    .as("not even the channel overwrite of the account")
                    .isFalse();
            assertThat(allowed(Rule.MANAGE_SERVER, input))
                    .as("not even the owner")
                    .isFalse();
        }
    }

    @Nested
    class ServerLayer {
        @Test
        void theOwnerShouldBeAllowedEverythingInTheOwnServer() {
            for (var rule : Rule.all()) {
                assertThat(allowed(rule, server(true))).as(rule.name()).isTrue();
            }
        }

        @Test
        void aRoleShouldGrantOrTakeAwayARule() {
            var input = server(
                    false, role(5, RuleSet.allowing(Rule.MANAGE_ROLES)), role(1, RuleSet.denying(Rule.SEND_MESSAGES)));

            assertThat(allowed(Rule.MANAGE_ROLES, input)).isTrue();
            assertThat(allowed(Rule.SEND_MESSAGES, input)).isFalse();
            assertThat(allowed(Rule.MANAGE_CHANNELS, input))
                    .as("undecided: the default")
                    .isFalse();
            assertThat(allowed(Rule.CONNECT, input))
                    .as("undecided: the default")
                    .isTrue();
        }

        @Test
        void theRoleWithTheHighestPositionShouldWin() {
            var higherAllows = server(
                    false, role(1, RuleSet.denying(Rule.SEND_MESSAGES)), role(9, RuleSet.allowing(Rule.SEND_MESSAGES)));
            var higherDenies = server(
                    false, role(1, RuleSet.allowing(Rule.MANAGE_ROLES)), role(9, RuleSet.denying(Rule.MANAGE_ROLES)));

            assertThat(allowed(Rule.SEND_MESSAGES, higherAllows)).isTrue();
            assertThat(allowed(Rule.MANAGE_ROLES, higherDenies)).isFalse();
        }

        @Test
        void aRoleThatSaysNothingShouldNotHideALowerRole() {
            var input = server(false, role(1, RuleSet.allowing(Rule.MANAGE_ROLES)), role(9, RuleSet.empty()));

            assertThat(allowed(Rule.MANAGE_ROLES, input)).isTrue();
        }

        @Test
        void rolesAtTheSamePositionShouldResolveToTheDenial() {
            var input = server(
                    false, role(3, RuleSet.allowing(Rule.SEND_MESSAGES)), role(3, RuleSet.denying(Rule.SEND_MESSAGES)));

            assertThat(allowed(Rule.SEND_MESSAGES, input)).isFalse();
        }
    }

    @Nested
    class ChannelLayer {
        private PermissionInput channel(List<RoleRules> serverRoles, List<RoleRules> overwrites, RuleSet own) {
            return new PermissionInput(Standing.MEMBER, RuleSet.empty(), false, serverRoles, overwrites, own);
        }

        @Test
        void aChannelOverwriteOfARoleShouldReplaceTheServerDecision() {
            var input = channel(
                    List.of(role(5, RuleSet.allowing(Rule.SEND_MESSAGES))),
                    List.of(role(5, RuleSet.denying(Rule.SEND_MESSAGES))),
                    null);

            assertThat(allowed(Rule.SEND_MESSAGES, input)).isFalse();
        }

        @Test
        void theOverwriteOfTheAccountShouldBeTheMostSpecific() {
            var input = channel(
                    List.of(role(5, RuleSet.denying(Rule.SEND_MESSAGES))),
                    List.of(role(5, RuleSet.denying(Rule.SEND_MESSAGES))),
                    RuleSet.allowing(Rule.SEND_MESSAGES));

            assertThat(allowed(Rule.SEND_MESSAGES, input)).isTrue();
        }

        @Test
        void anOverwriteThatSaysNothingShouldLeaveTheServerDecision() {
            var input = channel(
                    List.of(role(5, RuleSet.denying(Rule.SEND_MESSAGES))),
                    List.of(role(5, RuleSet.allowing(Rule.CONNECT))),
                    RuleSet.empty());

            assertThat(allowed(Rule.SEND_MESSAGES, input)).isFalse();
            assertThat(allowed(Rule.CONNECT, input)).isTrue();
        }

        @Test
        void aChannelOverwriteShouldNotLiftAnOwnerOverAPlatformDenialButTheOwnerIsNotLimitedByOverwrites() {
            var owner = new PermissionInput(
                    Standing.MEMBER,
                    RuleSet.empty(),
                    true,
                    List.of(),
                    List.of(role(1, RuleSet.denying(Rule.SEND_MESSAGES))),
                    RuleSet.denying(Rule.SEND_MESSAGES));

            assertThat(allowed(Rule.SEND_MESSAGES, owner))
                    .as("the owner is above channel overwrites")
                    .isTrue();
        }
    }

    @Test
    void ruleSetShouldReportWhatItDecides() {
        var set = RuleSet.allowing(Rule.CONNECT);

        assertThat(set.get(Rule.CONNECT)).isEqualTo(RuleState.ALLOW);
        assertThat(set.get(Rule.SPEAK)).isNull();
        assertThat(RuleSet.empty().isEmpty()).isTrue();
        assertThat(RuleSet.denyingAll().get(Rule.SPEAK)).isEqualTo(RuleState.DENY);
        assertThat(RuleSet.denying(Rule.SPEAK))
                .isEqualTo(RuleSet.denying(Rule.SPEAK))
                .isNotEqualTo(set);
    }
}
