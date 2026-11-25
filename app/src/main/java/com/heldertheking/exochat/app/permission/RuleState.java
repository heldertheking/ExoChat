package com.heldertheking.exochat.app.permission;

/// What a layer decides about a rule. A layer that decides nothing has no entry for the rule at all (the rule is
/// inherited), so there is no third value.
public enum RuleState {
    ALLOW,
    DENY
}
