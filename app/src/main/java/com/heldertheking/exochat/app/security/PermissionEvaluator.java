package com.heldertheking.exochat.app.security;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.SessionName;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.app.permission.PermissionInput;
import com.heldertheking.exochat.app.permission.PermissionRepository;
import com.heldertheking.exochat.app.permission.PermissionResolver;
import com.heldertheking.exochat.app.permission.Rule;

/// Answers who is calling and what that caller may do. The `*PermissionChecker` of each domain asks it, handlers
/// never look at the security context themselves.
@Service
public class PermissionEvaluator {
    private final PermissionRepository permissionRepository;

    public PermissionEvaluator(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    /// The authenticated caller.
    ///
    /// @throws AuthenticationCredentialsNotFoundException outside an authenticated call
    public AuthenticatedUser currentUser() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getPrincipal)
                .filter(AuthenticatedUser.class::isInstance)
                .map(AuthenticatedUser.class::cast)
                .orElseThrow(
                        () -> new AuthenticationCredentialsNotFoundException(
                                "Permission could not be evaluated as authentication is not set in the current SecurityContext"));
    }

    public UserName currentUserName() {
        return UserName.of(currentUser().getAttrs().userId());
    }

    /// The active account of the caller, null if its session has none.
    public @Nullable AccountName currentAccountName() {
        var accountId = currentUser().getAttrs().accountId();
        return accountId == null ? null : AccountName.of(currentUserName(), accountId);
    }

    public UUID currentSessionId() {
        return currentUser().getAttrs().sessionId();
    }

    public boolean isUser(UserName user) {
        return !user.isWildcard()
                && user.getPersistenceId().equals(currentUser().getAttrs().userId());
    }

    public boolean isAccount(AccountName account) {
        return !account.isWildcard()
                && isUser(account.getParent())
                && account.getPersistenceId().equals(currentUser().getAttrs().accountId());
    }

    /// True for the session the call was made with.
    public boolean isCurrentSession(SessionName session) {
        return !session.isWildcard()
                && isUser(session.getParent())
                && session.getPersistenceId().equals(currentSessionId());
    }

    /// Whether the active account of the caller may do the rule outside any server (platform layer only). False if
    /// the session has no active account.
    public boolean can(Rule rule) {
        var account = currentAccountName();
        return account != null && can(account, rule);
    }

    /// Whether the account may do the rule outside any server. Takes the subject explicitly so that code without a
    /// security context, such as an event consumer, can ask too. An account that does not exist may do nothing.
    public boolean can(AccountName account, Rule rule) {
        if (account.isWildcard()) {
            return false;
        }
        var accountId = account.getPersistenceId();
        return permissionRepository
                .standing(accountId)
                .map(standing -> PermissionResolver.isAllowed(
                        rule, PermissionInput.platform(standing, permissionRepository.accountOverrides(accountId))))
                .orElse(false);
    }
}
