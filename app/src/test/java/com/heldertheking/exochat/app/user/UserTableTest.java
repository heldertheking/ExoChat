package com.heldertheking.exochat.app.user;

import static com.heldertheking.exochat.db.schema.Tables.USER;
import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Pattern for DB tests: a narrow Spring context (no gRPC server), Liquibase migrating the
 * {@code exochat_test} database, and a prepared dataset loaded before each test method.
 */
@SpringBootTest(classes = UserTableTest.TestConfig.class)
@Sql(scripts = "classpath:/test-data/user/test-dataset.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UserTableTest {

    @SpringBootConfiguration
    @Import({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            LiquibaseAutoConfiguration.class,
            JooqAutoConfiguration.class,
    })
    static class TestConfig {
    }

    @Autowired
    private DSLContext dsl;

    @Test
    void readsPreparedDataset() {
        var names = dsl.select(USER.DISPLAY_NAME).from(USER).orderBy(USER.ID).fetch(USER.DISPLAY_NAME);

        assertThat(names).containsExactly("Alice Example", "Bob Example");
    }
}
