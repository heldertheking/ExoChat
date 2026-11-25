package com.heldertheking.exochat.app.identity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.RelationshipName;
import com.heldertheking.exochat.app.permission.Rule;
import com.heldertheking.exochat.app.security.PermissionEvaluator;

/// Who may use the relationships of an account: its user, with any of its accounts (not only the active one),
/// and nobody else. Sending a request also needs the `SEND_FRIEND_REQUESTS` rule for the sending account, so a
/// restricted account cannot reach out to new people. Blocking, answering and removing are never denied by a rule.
@Service
class RelationshipPermissionChecker {
    private final PermissionEvaluator permissionEvaluator;

    RelationshipPermissionChecker(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    void verifyListPermission(AccountName owner) {
        verifyOwner(owner, "list the relationships of");
    }

    void verifyReadPermission(RelationshipName name) {
        verifyOwner(name, "read");
    }

    void verifyCreatePermission(AccountName owner, AccountName target) {
        verifyOwner(owner, "send requests for");
        verifyTarget(target);
        if (!permissionEvaluator.can(owner, Rule.SEND_FRIEND_REQUESTS)) {
            throw new AccessDeniedException("account may not send friend requests");
        }
    }

    void verifyAnswerPermission(RelationshipName name) {
        verifyOwner(name, "answer");
    }

    void verifyDeletePermission(RelationshipName name) {
        verifyOwner(name, "delete");
    }

    void verifyBlockPermission(AccountName owner, AccountName target) {
        verifyOwner(owner, "block for");
        verifyTarget(target);
    }

    private static void verifyTarget(AccountName target) {
        if (target.isWildcard()) {
            throw new AccessDeniedException("wildcard account names are not allowed");
        }
    }

    private void verifyOwner(RelationshipName name, String action) {
        if (name.isWildcard()) {
            throw new AccessDeniedException("wildcard relationship names are not allowed");
        }
        verifyOwner(name.getParent(), action);
    }

    private void verifyOwner(AccountName owner, String action) {
        if (owner.isWildcard()) {
            throw new AccessDeniedException("wildcard account names are not allowed");
        }
        if (!permissionEvaluator.isUser(owner.getParent())) {
            throw new AccessDeniedException("user does not have permission to " + action + " account " + owner);
        }
    }
}
