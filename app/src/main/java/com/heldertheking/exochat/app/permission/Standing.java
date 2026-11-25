package com.heldertheking.exochat.app.permission;

/// The platform status of an account. It picks the base rule set the account starts from; the platform can refine it
/// with rules set on the account. Names match `AccountStanding` of the proto and the database.
public enum Standing {
    /// Uses the application normally.
    MEMBER(RuleSet.empty()),
    /// Limited by a general rule set: no new servers, invites or friend requests.
    RESTRICTED(RuleSet.denying(Rule.CREATE_SERVER, Rule.CREATE_INVITE, Rule.SEND_FRIEND_REQUESTS)),
    /// Can do nothing.
    SUSPENDED(RuleSet.denyingAll());

    private final RuleSet baseline;

    Standing(RuleSet baseline) {
        this.baseline = baseline;
    }

    /// The rules this standing decides on its own.
    public RuleSet baseline() {
        return baseline;
    }
}
