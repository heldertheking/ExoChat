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
class SessionPermissionCheckerTest {
    private static final UserName USER = UserName.of(UuidV7.next());
    private static final SessionName SESSION = SessionName.of(USER, UuidV7.next());
    private static final AccountName ACCOUNT = AccountName.of(USER, UuidV7.next());

    @Mock
    private PermissionEvaluator permissionEvaluator;

    @InjectMocks
    private SessionPermissionChecker checker;

    @Test
    void verifyListPermission_should_allowTheOwnUser() {
        when(permissionEvaluator.isUser(USER)).thenReturn(true);

        assertThatCode(() -> checker.verifyListPermission(USER)).doesNotThrowAnyException();
    }

    @Test
    void verifyListPermission_should_denyOtherUsers() {
        when(permissionEvaluator.isUser(USER)).thenReturn(false);

        assertThatThrownBy(() -> checker.verifyListPermission(USER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void verifyReadAndDelete_should_allowOwnSessionsOnly() {
        when(permissionEvaluator.isUser(USER)).thenReturn(true, true, false, false);

        assertThatCode(() -> checker.verifyReadPermission(SESSION)).doesNotThrowAnyException();
        assertThatCode(() -> checker.verifyDeletePermission(SESSION)).doesNotThrowAnyException();
        assertThatThrownBy(() -> checker.verifyReadPermission(SESSION)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> checker.verifyDeletePermission(SESSION)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void verifyReadAndDelete_should_denyWildcards() {
        var wildcard = SessionName.wildcard(UserName.wildcard());

        assertThatThrownBy(() -> checker.verifyReadPermission(wildcard))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("wildcard");
        assertThatThrownBy(() -> checker.verifyDeletePermission(wildcard))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("wildcard");
    }
}
