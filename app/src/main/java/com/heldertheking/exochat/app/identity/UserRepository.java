package com.heldertheking.exochat.app.identity;

import static com.heldertheking.exochat.db.schema.Tables.USER;

import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import com.heldertheking.exochat.db.schema.tables.records.UserRecord;

@Repository
class UserRepository {
    private final DSLContext dsl;

    UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    Optional<UserRecord> find(UUID id) {
        return dsl.selectFrom(USER).where(USER.ID.eq(id)).fetchOptional();
    }

    /// Stores the changed fields of the record.
    void update(UserRecord user) {
        user.attach(dsl.configuration());
        user.update();
    }

    /// Deletes the user with its accounts, sessions and relationships (foreign keys cascade). True if it existed.
    boolean delete(UUID id) {
        return dsl.deleteFrom(USER).where(USER.ID.eq(id)).execute() > 0;
    }
}
