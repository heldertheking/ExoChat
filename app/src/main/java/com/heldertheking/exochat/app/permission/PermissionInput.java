package com.heldertheking.exochat.app.permission;

import java.util.List;

import org.jspecify.annotations.Nullable;

/// Everything the [PermissionResolver] needs to decide one rule for one account, already loaded.
///
/// @param standing platform standing of the account
/// @param accountOverrides rules the platform set on the account, they refine the standing
/// @param serverOwner the account owns the server (never above the platform layer)
/// @param serverRoles the roles of the account in the server, including the default role of the server (give it
///     the lowest position), empty outside a server
/// @param channelRoleOverwrites overwrites of the channel that target one of those roles, empty outside a channel
/// @param channelAccountOverwrite the overwrite of the channel that targets the account itself, null if there is
///     none
public record PermissionInput(
        Standing standing,
        RuleSet accountOverrides,
        boolean serverOwner,
        List<RoleRules> serverRoles,
        List<RoleRules> channelRoleOverwrites,
        @Nullable RuleSet channelAccountOverwrite) {

    /// Input for a decision outside any server: only the platform layer applies.
    public static PermissionInput platform(Standing standing, RuleSet accountOverrides) {
        return new PermissionInput(standing, accountOverrides, false, List.of(), List.of(), null);
    }

    /// The rules of one role (or of the overwrite of one role) and the position of the role. A higher position
    /// outranks a lower one.
    public record RoleRules(int position, RuleSet rules) {}
}
