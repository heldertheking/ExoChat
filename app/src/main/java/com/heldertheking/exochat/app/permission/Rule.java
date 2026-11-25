package com.heldertheking.exochat.app.permission;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// An action that can be allowed or denied, like `SEND_MESSAGES`. A rule is a plain value with a name, in the style of
/// the `Permission` record of app-gateway: the rules the application knows are constants on this record, and new ones
/// (for example from an extension, ADR-008) are one line more, not a new enum value everywhere.
///
/// The names match the `Rule` enum of `common/v1alpha1/rule.proto` (a test keeps them in sync) and are what the
/// database stores. Each rule says what applies when no layer has an opinion: most are allowed by default, the ones
/// that manage something are denied unless a role grants them.
///
/// @param name             unique name, upper case with underscores
/// @param allowedByDefault whether the rule is allowed when no layer sets it
public record Rule(String name, boolean allowedByDefault) {
    private static final Map<String, Rule> REGISTRY = new LinkedHashMap<>();

    public static final Rule CREATE_SERVER = register("CREATE_SERVER", true);
    public static final Rule SEND_FRIEND_REQUESTS = register("SEND_FRIEND_REQUESTS", true);
    public static final Rule CREATE_INVITE = register("CREATE_INVITE", true);
    public static final Rule MANAGE_SERVER = register("MANAGE_SERVER", false);
    public static final Rule MANAGE_ROLES = register("MANAGE_ROLES", false);
    public static final Rule MANAGE_CHANNELS = register("MANAGE_CHANNELS", false);
    public static final Rule MANAGE_MEMBERS = register("MANAGE_MEMBERS", false);
    public static final Rule VIEW_CHANNEL = register("VIEW_CHANNEL", true);
    public static final Rule SEND_MESSAGES = register("SEND_MESSAGES", true);
    public static final Rule MANAGE_MESSAGES = register("MANAGE_MESSAGES", false);
    public static final Rule CONNECT = register("CONNECT", true);
    public static final Rule SPEAK = register("SPEAK", true);
    public static final Rule CREATE_ACCOUNT = register("CREATE_ACCOUNT", true);

    public Rule {
        if (name.isBlank()) {
            throw new IllegalArgumentException("a rule needs a name");
        }
    }

    private static Rule register(String name, boolean allowedByDefault) {
        var rule = new Rule(name, allowedByDefault);
        if (REGISTRY.putIfAbsent(name, rule) != null) {
            throw new IllegalStateException("duplicate rule " + name);
        }
        return rule;
    }

    /// Every rule the application knows, in the order they are declared.
    public static List<Rule> all() {
        return List.copyOf(REGISTRY.values());
    }

    /// The known rule with the name, empty for a name the application does not know (for example one that was
    /// removed since the row was written).
    public static Optional<Rule> find(String name) {
        return Optional.ofNullable(REGISTRY.get(name));
    }

    @Override
    public String toString() {
        return name;
    }
}
