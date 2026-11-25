package com.heldertheking.exochat.app.permission;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.heldertheking.exochat.app.permission.PermissionInput.RoleRules;

/// Decides whether a rule is allowed. A pure function of the loaded layers, it knows nothing of databases, Spring or
/// gRPC so that every module that needs a decision can use it.
///
/// The layers, from the most general to the most specific:
///
/// 1. **Platform.** The rules set on the account win over its standing. A `DENY` here is a ceiling: nothing below
///    can lift it, a server owner cannot un-restrict an account the platform restricted. An `ALLOW` only lifts a
///    denial of the standing, it grants nothing by itself.
/// 2. **Server owner.** Allowed, everything in the owner's own server.
/// 3. **Server roles.** Of all roles of the account that decide the rule, the one with the highest position wins.
/// 4. **Channel.** Overwrites of roles (highest position wins), then the overwrite of the account itself. Each step
///    replaces the decision of the steps before it.
///
/// If no layer decides, the default of the [Rule] applies.
public final class PermissionResolver {
    private PermissionResolver() {}

    public static boolean isAllowed(Rule rule, PermissionInput input) {
        var platform = input.accountOverrides().get(rule);
        if (platform == null) {
            platform = input.standing().baseline().get(rule);
        }
        if (platform == RuleState.DENY) {
            return false;
        }
        if (input.serverOwner()) {
            return true;
        }
        RuleState state = null;
        state = override(state, highestPosition(input.serverRoles(), rule));
        state = override(state, highestPosition(input.channelRoleOverwrites(), rule));
        var own = input.channelAccountOverwrite();
        state = override(state, own == null ? null : own.get(rule));
        return state == null ? rule.allowedByDefault() : state == RuleState.ALLOW;
    }

    private static @Nullable RuleState override(@Nullable RuleState current, @Nullable RuleState more) {
        return more != null ? more : current;
    }

    /// The decision of the role with the highest position that decides the rule. Roles with the same position are a
    /// conflict the data should not contain; the denial wins then, the safe side.
    private static @Nullable RuleState highestPosition(List<RoleRules> roles, Rule rule) {
        RoleRules best = null;
        RuleState bestState = null;
        for (var role : roles) {
            var state = role.rules().get(rule);
            if (state == null) {
                continue;
            }
            if (best == null
                    || role.position() > best.position()
                    || (role.position() == best.position() && state == RuleState.DENY)) {
                best = role;
                bestState = state;
            }
        }
        return bestState;
    }
}
