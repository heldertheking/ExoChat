package com.heldertheking.exochat.app.permission;

import java.util.Optional;
import java.util.UUID;

/// Loads the layers the [PermissionResolver] decides on. Server and channel layers are added with the tables of the
/// community and messaging domains.
public interface PermissionRepository {

    /// The platform standing of the account, empty if the account does not exist.
    Optional<Standing> standing(UUID accountId);

    /// The rules the platform set on the account.
    RuleSet accountOverrides(UUID accountId);
}
