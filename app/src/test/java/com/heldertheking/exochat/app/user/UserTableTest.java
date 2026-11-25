package com.heldertheking.exochat.app.user;

import static com.heldertheking.exochat.db.schema.Tables.USER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import com.heldertheking.exochat.api.id.UuidV7;

/**
 * Pattern for DB tests: a narrow Spring context (no gRPC server), Liquibase migrating the
 * {@code exochat_test} database, and a prepared dataset loaded before each test method.
 */
@SpringBootTest(classes = UserTableTest.TestConfig.class)
@Sql(scripts = "classpath:/test-data/user/test-dataset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UserTableTest {

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

    @Test
    void readsPreparedDataset() {
        var names = dsl.select(USER.DISPLAY_NAME).from(USER).orderBy(USER.ID).fetch(USER.DISPLAY_NAME);

        assertThat(names).containsExactly("Alice Example", "Bob Example");
    }

    @Test
    void storesUuidV7IdsInCreationOrder() {
        var created = new ArrayList<UUID>();
        for (int i = 0; i < 300; i++) {
            created.add(UuidV7.next());
        }
        var shuffled = new ArrayList<>(created);
        Collections.shuffle(shuffled);
        dsl.deleteFrom(USER).execute();
        for (var id : shuffled) {
            dsl.insertInto(USER)
                    .set(USER.ID, id)
                    .set(USER.DISPLAY_NAME, id.toString())
                    .execute();
        }

        var ids = dsl.select(USER.ID).from(USER).orderBy(USER.ID).fetch(USER.ID);

        // The database sorts v7 ids like the generator created them, so keyset pagination on id works.
        assertThat(ids).containsExactlyElementsOf(created);
    }
}
