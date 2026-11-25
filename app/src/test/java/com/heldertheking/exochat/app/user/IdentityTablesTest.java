package com.heldertheking.exochat.app.user;

import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT;
import static com.heldertheking.exochat.db.schema.Tables.RELATIONSHIP;
import static com.heldertheking.exochat.db.schema.Tables.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.jooq.DSLContext;
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

import com.heldertheking.exochat.api.id.UuidV7;

/// Checks the constraints of the identity schema (user, account, relationship) against the test database.
@SpringBootTest(classes = IdentityTablesTest.TestConfig.class)
class IdentityTablesTest {

    @SpringBootConfiguration
    @Import({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        LiquibaseAutoConfiguration.class,
        JooqAutoConfiguration.class,
    })
    static class TestConfig {}

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void clean() {
        // accounts and relationships go with their user (ON DELETE CASCADE)
        dsl.deleteFrom(USER).execute();
    }

    @Test
    void userEmailAndPhoneNumberAreUnique() {
        insertUser("a@example.com", "+351000000001");

        assertThatThrownBy(() -> insertUser("a@example.com", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser("b@example.com", "+351000000001"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // phone number is optional and several users may have none
        insertUser("c@example.com", null);
        insertUser("d@example.com", null);
    }

    @Test
    void usernameIsGloballyUnique() {
        var user1 = insertUser("a@example.com", null);
        var user2 = insertUser("b@example.com", null);
        insertAccount(user1, "alice");

        assertThatThrownBy(() -> insertAccount(user2, "alice")).isInstanceOf(DataIntegrityViolationException.class);
        insertAccount(user1, "alice2");
    }

    @Test
    void deletingAUserDeletesItsAccountsAndRelationships() {
        var user = insertUser("a@example.com", null);
        var other = insertUser("b@example.com", null);
        var account = insertAccount(user, "alice");
        var otherAccount = insertAccount(other, "bob");
        insertRelationship(account, otherAccount, "PENDING_OUTGOING");
        insertRelationship(otherAccount, account, "PENDING_INCOMING");

        dsl.deleteFrom(USER).where(USER.ID.eq(user)).execute();

        assertThat(dsl.fetchCount(ACCOUNT)).isEqualTo(1);
        assertThat(dsl.fetchCount(RELATIONSHIP))
                .as("links of the deleted account, both directions")
                .isZero();
    }

    @Test
    void relationshipRules() {
        var user = insertUser("a@example.com", null);
        var a = insertAccount(user, "alice");
        var b = insertAccount(user, "bob");
        insertRelationship(a, b, "FRIEND");

        assertThatThrownBy(() -> insertRelationship(a, b, "BLOCKED"))
                .as("one relationship per owner and target")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRelationship(a, a, "FRIEND"))
                .as("no relationship with yourself")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRelationship(b, a, "ENEMY"))
                .as("type is one of the RelationshipType values")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRelationship(b, UuidV7.next(), "FRIEND"))
                .as("target must exist")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateTimeChangesOnUpdate() throws InterruptedException {
        var user = insertUser("a@example.com", null);
        var account = insertAccount(user, "alice");
        var before = dsl.select(ACCOUNT.UPDATE_TIME).from(ACCOUNT).fetchOne(ACCOUNT.UPDATE_TIME);

        Thread.sleep(5);
        dsl.update(ACCOUNT)
                .set(ACCOUNT.DISPLAY_NAME, "Alice Changed")
                .where(ACCOUNT.ID.eq(account))
                .execute();

        var after = dsl.select(ACCOUNT.UPDATE_TIME).from(ACCOUNT).fetchOne(ACCOUNT.UPDATE_TIME);
        assertThat(after).isAfter(before);
    }

    private UUID insertUser(String email, String phoneNumber) {
        var id = UuidV7.next();
        dsl.insertInto(USER)
                .set(USER.ID, id)
                .set(USER.EMAIL, email)
                .set(USER.PHONE_NUMBER, phoneNumber)
                .set(USER.PASSWORD_HASH, "hash")
                .execute();
        return id;
    }

    private UUID insertAccount(UUID userId, String username) {
        var id = UuidV7.next();
        dsl.insertInto(ACCOUNT)
                .set(ACCOUNT.ID, id)
                .set(ACCOUNT.USER_ID, userId)
                .set(ACCOUNT.USERNAME, username)
                .set(ACCOUNT.DISPLAY_NAME, username)
                .execute();
        return id;
    }

    private void insertRelationship(UUID owner, UUID target, String type) {
        dsl.insertInto(RELATIONSHIP)
                .set(RELATIONSHIP.ID, UuidV7.next())
                .set(RELATIONSHIP.OWNER_ACCOUNT_ID, owner)
                .set(RELATIONSHIP.TARGET_ACCOUNT_ID, target)
                .set(RELATIONSHIP.TYPE, type)
                .execute();
    }
}
