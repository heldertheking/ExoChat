package com.heldertheking.exochat.app.auth;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.heldertheking.exochat.api.identity.SessionName;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.app.security.PermissionEvaluator;

/// Who may list, read and revoke sessions: a user always, and only, its own. There is no rule to take that away,
/// a restricted user must still be able to see and end its sessions.
@Service
class SessionPermissionChecker {
    private final PermissionEvaluator permissionEvaluator;

    SessionPermissionChecker(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    void verifyListPermission(UserName parent) {
        if (!permissionEvaluator.isUser(parent)) {
            throw new AccessDeniedException("user does not have permission to list the sessions of " + parent);
        }
    }

    void verifyReadPermission(SessionName name) {
        if (name.isWildcard()) {
            throw new AccessDeniedException("wildcard session names are not allowed");
        }
        if (!permissionEvaluator.isUser(name.getParent())) {
            throw new AccessDeniedException("user does not have permission to read session " + name);
        }
    }

    void verifyDeletePermission(SessionName name) {
        if (name.isWildcard()) {
            throw new AccessDeniedException("wildcard session names are not allowed");
        }
        if (!permissionEvaluator.isUser(name.getParent())) {
            throw new AccessDeniedException("user does not have permission to delete session " + name);
        }
    }
}
