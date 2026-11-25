package com.heldertheking.exochat.app.permission;

/// An action that can be allowed or denied. The names match the `Rule` enum of `common/v1alpha1/rule.proto` (a test
/// keeps them in sync) and are what the database stores.
///
/// Each rule says what applies when no layer has an opinion: most rules are allowed by default, the ones that
/// manage something are denied unless a role grants them.
public enum Rule {
    CREATE_SERVER(true),
    SEND_FRIEND_REQUESTS(true),
    CREATE_INVITE(true),
    MANAGE_SERVER(false),
    MANAGE_ROLES(false),
    MANAGE_CHANNELS(false),
    MANAGE_MEMBERS(false),
    VIEW_CHANNEL(true),
    SEND_MESSAGES(true),
    MANAGE_MESSAGES(false),
    CONNECT(true),
    SPEAK(true);

    private final boolean allowedByDefault;

    Rule(boolean allowedByDefault) {
        this.allowedByDefault = allowedByDefault;
    }

    /// True if the rule is allowed when no layer sets it.
    public boolean allowedByDefault() {
        return allowedByDefault;
    }
}
