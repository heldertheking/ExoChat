package com.heldertheking.exochat.app.permission.persistence;

import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT;
import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT_RULE;
import static com.heldertheking.exochat.db.schema.Tables.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;

import com.heldertheking.exochat.api.id.UuidV7;
import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.app.permission.Rule;
import com.heldertheking.exochat.app.security.AuthenticatedUser;
import com.heldertheking.exochat.app.security.PermissionEvaluator;
import com.heldertheking.exochat.app.security.TokenAttributes;

/// The platform layer end to end: standing and rules in MariaDB, resolved by the evaluator.
@SpringBootTest(classes = PermissionEvaluatorIntegrationTest.TestConfig.class)
class PermissionEvaluatorIntegrationTest {

    @SpringBootConfiguration
    @Import({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        LiquibaseAutoConfiguration.class,
        JooqAutoConfiguration.class,
        JooqPermissionRepository.class,
        PermissionEvaluator.class,
    })
    static class TestConfig {}

    @Autowired
    private DSLContext dsl;

    @Autowired
    private PermissionEvaluator evaluator;

    private UUID userId;
    private AccountName account;

    @BeforeEach
    void setUp() {
        dsl.deleteFrom(USER).execute();
        userId = UuidV7.next();
        dsl.insertInto(USER)
                .set(USER.ID, userId)
                .set(USER.EMAIL, "a@example.com")
                .set(USER.PASSWORD_HASH, "hash")
                .execute();
        account = insertAccount("alice");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private AccountName insertAccount(String username) {
        var id = UuidV7.next();
        dsl.insertInto(ACCOUNT)
                .set(ACCOUNT.ID, id)
                .set(ACCOUNT.USER_ID, userId)
                .set(ACCOUNT.USERNAME, username)
                .set(ACCOUNT.DISPLAY_NAME, username)
                .execute();
        return AccountName.of(UserName.of(userId), id);
    }

    private void setStanding(String standing) {
        dsl.update(ACCOUNT)
                .set(ACCOUNT.STANDING, standing)
                .where(ACCOUNT.ID.eq(account.getPersistenceId()))
                .execute();
    }

    private void setRule(String rule, String state) {
        dsl.insertInto(ACCOUNT_RULE)
                .set(ACCOUNT_RULE.ACCOUNT_ID, account.getPersistenceId())
                .set(ACCOUNT_RULE.RULE, rule)
                .set(ACCOUNT_RULE.STATE, state)
                .execute();
    }

    @Test
    void aMemberShouldUseTheApplicationNormally() {
        assertThat(evaluator.can(account, Rule.CREATE_SERVER)).isTrue();
        assertThat(evaluator.can(account, Rule.SEND_MESSAGES)).isTrue();
        assertThat(evaluator.can(account, Rule.MANAGE_SERVER))
                .as("denied by default")
                .isFalse();
    }

    @Test
    void aRestrictedAccountShouldBeLimitedAndAnOverrideShouldLiftIt() {
        setStanding("RESTRICTED");

        assertThat(evaluator.can(account, Rule.CREATE_SERVER)).isFalse();
        assertThat(evaluator.can(account, Rule.SEND_MESSAGES)).isTrue();

        setRule("CREATE_SERVER", "ALLOW");

        assertThat(evaluator.can(account, Rule.CREATE_SERVER)).isTrue();
        assertThat(evaluator.can(account, Rule.CREATE_INVITE)).isFalse();
    }

    @Test
    void aSuspendedAccountShouldBeAbleToDoNothing() {
        setStanding("SUSPENDED");

        assertThat(evaluator.can(account, Rule.SEND_MESSAGES)).isFalse();
        assertThat(evaluator.can(account, Rule.VIEW_CHANNEL)).isFalse();
    }

    @Test
    void anOverrideShouldDenyAMemberOneRule() {
        setRule("SEND_MESSAGES", "DENY");

        assertThat(evaluator.can(account, Rule.SEND_MESSAGES)).isFalse();
        assertThat(evaluator.can(account, Rule.CONNECT)).isTrue();
    }

    @Test
    void unknownRulesInTheDatabaseShouldBeIgnored() {
        setRule("A_RULE_FROM_THE_FUTURE", "DENY");
        setRule("SPEAK", "DENY");

        assertThat(evaluator.can(account, Rule.CONNECT)).isTrue();
        assertThat(evaluator.can(account, Rule.SPEAK)).isFalse();
    }

    @Test
    void anAccountThatDoesNotExistOrAWildcardShouldBeAllowedNothing() {
        var missing = AccountName.of(UserName.of(userId), UuidV7.next());

        assertThat(evaluator.can(missing, Rule.SEND_MESSAGES)).isFalse();
        assertThat(evaluator.can(AccountName.wildcard(UserName.wildcard()), Rule.SEND_MESSAGES))
                .isFalse();
    }

    @Test
    void canOfTheCallerShouldUseTheActiveAccountOfTheSecurityContext() {
        authenticate(account.getPersistenceId());
        setRule("SEND_MESSAGES", "DENY");

        assertThat(evaluator.can(Rule.SEND_MESSAGES)).isFalse();
        assertThat(evaluator.can(Rule.CONNECT)).isTrue();
        assertThat(evaluator.currentAccountName()).isEqualTo(account);
        assertThat(evaluator.isAccount(account)).isTrue();
        assertThat(evaluator.isUser(UserName.of(userId))).isTrue();
    }

    @Test
    void aSessionWithoutAnActiveAccountShouldBeAllowedNothing() {
        authenticate(null);

        assertThat(evaluator.can(Rule.SEND_MESSAGES)).isFalse();
        assertThat(evaluator.currentAccountName()).isNull();
    }

    @Test
    void currentUserShouldFailOutsideAnAuthenticatedCall() {
        assertThatThrownBy(() -> evaluator.currentUser())
                .isInstanceOf(
                        org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void theDatabaseShouldRejectInvalidStandingsAndStates() {
        assertThatThrownBy(() -> setStanding("GOD")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> setRule("SEND_MESSAGES", "MAYBE")).isInstanceOf(DataIntegrityViolationException.class);
        setRule("SEND_MESSAGES", "DENY");
        assertThatThrownBy(() -> setRule("SEND_MESSAGES", "ALLOW"))
                .as("one decision per account and rule")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAnAccountShouldDeleteItsRules() {
        setRule("SEND_MESSAGES", "DENY");

        dsl.deleteFrom(ACCOUNT).where(ACCOUNT.ID.eq(account.getPersistenceId())).execute();

        assertThat(dsl.fetchCount(ACCOUNT_RULE)).isZero();
    }

    private void authenticate(UUID accountId) {
        var now = Instant.now();
        var user = new AuthenticatedUser(
                new TokenAttributes(userId, UuidV7.next(), accountId, now, now.plusSeconds(900)), List.of());
        var credentials = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", now, now.plusSeconds(900));
        SecurityContextHolder.getContext()
                .setAuthentication(new BearerTokenAuthentication(user, credentials, user.getAuthorities()));
    }
}
