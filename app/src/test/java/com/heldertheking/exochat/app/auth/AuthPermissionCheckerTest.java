package com.heldertheking.exochat.app.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.heldertheking.exochat.api.id.UuidV7;
import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.SessionName;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.app.security.PermissionEvaluator;

@ExtendWith(MockitoExtension.class)
class AuthPermissionCheckerTest {
    private static final UserName USER = UserName.of(UuidV7.next());
    private static final SessionName SESSION = SessionName.of(USER, UuidV7.next());
    private static final AccountName ACCOUNT = AccountName.of(USER, UuidV7.next());

    @Mock
    private PermissionEvaluator permissionEvaluator;

    @InjectMocks
    private AuthPermissionChecker checker;

    @Test
    void verifySwitchAccountPermission_should_requireTheCurrentSessionAndAnOwnAccount() {
        when(permissionEvaluator.isCurrentSession(SESSION)).thenReturn(true);
        when(permissionEvaluator.isUser(USER)).thenReturn(true);

        assertThatCode(() -> checker.verifySwitchAccountPermission(SESSION, ACCOUNT))
                .doesNotThrowAnyException();
    }

    @Test
    void verifySwitchAccountPermission_should_denyAnotherSession() {
        when(permissionEvaluator.isCurrentSession(SESSION)).thenReturn(false);

        assertThatThrownBy(() -> checker.verifySwitchAccountPermission(SESSION, ACCOUNT))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void verifySwitchAccountPermission_should_denyAnAccountOfAnotherUser() {
        var foreign = AccountName.of(UserName.of(UuidV7.next()), UuidV7.next());
        when(permissionEvaluator.isCurrentSession(SESSION)).thenReturn(true);
        when(permissionEvaluator.isUser(foreign.getParent())).thenReturn(false);

        assertThatThrownBy(() -> checker.verifySwitchAccountPermission(SESSION, foreign))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void verifyChangePasswordPermission_should_allowTheOwnUserOnly() {
        when(permissionEvaluator.isUser(USER)).thenReturn(true, false);

        assertThatCode(() -> checker.verifyChangePasswordPermission(USER)).doesNotThrowAnyException();
        assertThatThrownBy(() -> checker.verifyChangePasswordPermission(USER))
                .isInstanceOf(AccessDeniedException.class);
    }
}
