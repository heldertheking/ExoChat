package com.heldertheking.exochat.app.auth;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.SessionName;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.app.security.PermissionEvaluator;

/// Who may use the calls that act on the credentials of the caller. Registering, logging in and refreshing need no
/// permission, they are open (see [AuthSecurityConfiguration]).
@Service
class AuthPermissionChecker {
    private final PermissionEvaluator permissionEvaluator;

    AuthPermissionChecker(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    /// The session must be the one of the call and the account one of the same user.
    void verifySwitchAccountPermission(SessionName session, AccountName account) {
        if (!permissionEvaluator.isCurrentSession(session)) {
            throw new AccessDeniedException("only the session of the caller can be changed");
        }
        if (account.isWildcard() || !permissionEvaluator.isUser(account.getParent())) {
            throw new AccessDeniedException("user does not have permission to use account " + account);
        }
    }

    void verifyChangePasswordPermission(UserName user) {
        if (!permissionEvaluator.isUser(user)) {
            throw new AccessDeniedException("user does not have permission to change the password of " + user);
        }
    }
}
