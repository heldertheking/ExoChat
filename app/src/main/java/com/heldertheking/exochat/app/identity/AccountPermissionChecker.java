package com.heldertheking.exochat.app.identity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.app.permission.Rule;
import com.heldertheking.exochat.app.security.PermissionEvaluator;

/// Who may use accounts. An account is a public persona, so any authenticated caller may read one; the standing is
/// only shown to the owner. Listing, creating, changing and deleting are for the owner, and creating also needs the
/// `CREATE_ACCOUNT` rule.
@Service
class AccountPermissionChecker {
    private final PermissionEvaluator permissionEvaluator;

    AccountPermissionChecker(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    void verifyListPermission(UserName parent) {
        if (parent.isWildcard()) {
            throw new AccessDeniedException("wildcard user names are not allowed");
        }
        if (!permissionEvaluator.isUser(parent)) {
            throw new AccessDeniedException("user does not have permission to list the accounts of " + parent);
        }
    }

    void verifyReadPermission(AccountName name) {
        if (name.isWildcard()) {
            throw new AccessDeniedException("wildcard account names are not allowed");
        }
    }

    /// Whether the caller owns the account and may therefore see its standing.
    boolean isOwner(AccountName name) {
        return permissionEvaluator.isUser(name.getParent());
    }

    void verifyCreatePermission(UserName parent) {
        if (parent.isWildcard()) {
            throw new AccessDeniedException("wildcard user names are not allowed");
        }
        if (!permissionEvaluator.isUser(parent)) {
            throw new AccessDeniedException("user does not have permission to create accounts for " + parent);
        }
        if (!permissionEvaluator.can(Rule.CREATE_ACCOUNT)) {
            throw new AccessDeniedException("account may not create accounts");
        }
    }

    void verifyUpdatePermission(AccountName name) {
        verifyOwner(name, "update");
    }

    void verifyDeletePermission(AccountName name) {
        verifyOwner(name, "delete");
    }

    private void verifyOwner(AccountName name, String action) {
        if (name.isWildcard()) {
            throw new AccessDeniedException("wildcard account names are not allowed");
        }
        if (!isOwner(name)) {
            throw new AccessDeniedException("user does not have permission to " + action + " account " + name);
        }
    }
}
