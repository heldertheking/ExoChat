package com.heldertheking.exochat.app.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.heldertheking.exochat.api.id.UuidV7;
import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.app.permission.Rule;
import com.heldertheking.exochat.app.security.PermissionEvaluator;

@ExtendWith(MockitoExtension.class)
class IdentityPermissionCheckersTest {
    private static final UserName USER = UserName.of(UuidV7.next());
    private static final AccountName ACCOUNT = AccountName.of(USER, UuidV7.next());

    @Mock
    private PermissionEvaluator permissionEvaluator;

    @Test
    void userChecker_should_allowOnlyTheOwnUser() {
        var checker = new UserPermissionChecker(permissionEvaluator);
        when(permissionEvaluator.isUser(USER)).thenReturn(true, true, true, false, false, false);

        assertThatCode(() -> checker.verifyReadPermission(USER)).doesNotThrowAnyException();
        assertThatCode(() -> checker.verifyUpdatePermission(USER)).doesNotThrowAnyException();
        assertThatCode(() -> checker.verifyDeletePermission(USER)).doesNotThrowAnyException();
        assertThatThrownBy(() -> checker.verifyReadPermission(USER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> checker.verifyUpdatePermission(USER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> checker.verifyDeletePermission(USER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void userChecker_should_denyWildcards() {
        var checker = new UserPermissionChecker(permissionEvaluator);

        assertThatThrownBy(() -> checker.verifyReadPermission(UserName.wildcard()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("wildcard");
    }

    @Test
    void accountChecker_should_letEveryoneReadButOnlyTheOwnerSeeTheStanding() {
        var checker = new AccountPermissionChecker(permissionEvaluator);
        when(permissionEvaluator.isUser(USER)).thenReturn(true, false);

        assertThatCode(() -> checker.verifyReadPermission(ACCOUNT)).doesNotThrowAnyException();
        assertThat(checker.isOwner(ACCOUNT)).isTrue();
        assertThat(checker.isOwner(ACCOUNT)).isFalse();
        assertThatThrownBy(() -> checker.verifyReadPermission(AccountName.wildcard(UserName.wildcard())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void accountChecker_should_limitListUpdateAndDeleteToTheOwner() {
        var checker = new AccountPermissionChecker(permissionEvaluator);
        when(permissionEvaluator.isUser(USER)).thenReturn(true, true, true, false, false, false);

        assertThatCode(() -> checker.verifyListPermission(USER)).doesNotThrowAnyException();
        assertThatCode(() -> checker.verifyUpdatePermission(ACCOUNT)).doesNotThrowAnyException();
        assertThatCode(() -> checker.verifyDeletePermission(ACCOUNT)).doesNotThrowAnyException();
        assertThatThrownBy(() -> checker.verifyListPermission(USER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> checker.verifyUpdatePermission(ACCOUNT)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> checker.verifyDeletePermission(ACCOUNT)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void accountChecker_should_requireTheCreateAccountRuleToCreate() {
        var checker = new AccountPermissionChecker(permissionEvaluator);
        when(permissionEvaluator.isUser(USER)).thenReturn(true);
        when(permissionEvaluator.can(Rule.CREATE_ACCOUNT)).thenReturn(true, false);

        assertThatCode(() -> checker.verifyCreatePermission(USER)).doesNotThrowAnyException();
        assertThatThrownBy(() -> checker.verifyCreatePermission(USER))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("may not create");
    }

    @Test
    void accountChecker_should_notCreateForAnotherUser() {
        var checker = new AccountPermissionChecker(permissionEvaluator);
        when(permissionEvaluator.isUser(USER)).thenReturn(false);

        assertThatThrownBy(() -> checker.verifyCreatePermission(USER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void identityInput_should_normalizeAndValidate() {
        assertThat(IdentityInput.normalizeEmail("  A@B.Com ")).isEqualTo("a@b.com");
        assertThat(IdentityInput.normalizeEmail("nope")).isNull();
        assertThat(IdentityInput.normalizeEmail("a@b.com ".repeat(100))).isNull();
        assertThat(IdentityInput.requireUsername(" Some_Name.1 ")).isEqualTo("some_name.1");
        assertThatThrownBy(() -> IdentityInput.requireUsername("no"))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
        assertThatThrownBy(() -> IdentityInput.requireUsername("UPPER CASE"))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
        assertThat(IdentityInput.optionalPhoneNumber("  ")).isNull();
        assertThat(IdentityInput.optionalDisplayName(" Name ")).isEqualTo("Name");
        assertThatThrownBy(() -> IdentityInput.optionalDisplayName("x".repeat(65)))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
        assertThatThrownBy(() -> IdentityInput.optionalAvatarUrl("x".repeat(2049)))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
    }
}
