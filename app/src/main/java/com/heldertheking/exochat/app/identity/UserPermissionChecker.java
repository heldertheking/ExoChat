package com.heldertheking.exochat.app.identity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.app.security.PermissionEvaluator;

/// Who may read, change and delete a user: the user itself and nobody else. The email and phone number are private,
/// other users only ever see accounts.
@Service
class UserPermissionChecker {
    private final PermissionEvaluator permissionEvaluator;

    UserPermissionChecker(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    void verifyReadPermission(UserName name) {
        verifyOwn(name, "read");
    }

    void verifyUpdatePermission(UserName name) {
        verifyOwn(name, "update");
    }

    void verifyDeletePermission(UserName name) {
        verifyOwn(name, "delete");
    }

    private void verifyOwn(UserName name, String action) {
        if (name.isWildcard()) {
            throw new AccessDeniedException("wildcard user names are not allowed");
        }
        if (!permissionEvaluator.isUser(name)) {
            throw new AccessDeniedException("user does not have permission to " + action + " user " + name);
        }
    }
}
