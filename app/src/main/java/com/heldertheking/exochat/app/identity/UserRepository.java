package com.heldertheking.exochat.app.identity;

import static com.heldertheking.exochat.db.schema.Tables.USER;

import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.google.protobuf.FieldMask;
import com.heldertheking.exochat.app.grpc.MaskedMapper;
import com.heldertheking.exochat.db.schema.tables.records.UserRecord;
import com.heldertheking.exochat.identity.v1alpha1.User;

import io.grpc.Status;

/// The service layer of the users: reads, changes and deletes them with jOOQ. Problems are
/// [io.grpc.StatusRuntimeException]s with the status the caller should see.
@Repository
class UserRepository {
    private final DSLContext dsl;

    UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    Optional<UserRecord> find(UUID id) {
        return dsl.selectFrom(USER).where(USER.ID.eq(id)).fetchOptional();
    }

    /// Updates `email` and `phone_number`. With a mask exactly the named properties change (an empty
    /// `phone_number` clears it), without one the properties that are set in `changes` do.
    ///
    /// @throws io.grpc.StatusRuntimeException NOT_FOUND, INVALID_ARGUMENT for an invalid value, ALREADY_EXISTS if
    ///         the email or phone number is taken
    /// @throws com.heldertheking.exochat.app.grpc.UnmappedPropertyException if the mask names another property
    UserRecord update(UUID id, User changes, @Nullable FieldMask mask) {
        var user = find(id).orElseThrow(
                        () -> Status.NOT_FOUND.withDescription("user not found").asRuntimeException());
        boolean explicit = mask != null;
        MaskedMapper.forMask(mask)
                .setIfMaskedAndPresent(
                        "email",
                        explicit || !changes.getEmail().isEmpty(),
                        () -> IdentityInput.requireEmail(changes.getEmail()),
                        user::setEmail)
                .setIfMaskedAndPresent(
                        "phone_number",
                        explicit || !changes.getPhoneNumber().isEmpty(),
                        () -> IdentityInput.optionalPhoneNumber(changes.getPhoneNumber()),
                        user::setPhoneNumber)
                .throwOnUnmappedProperty();
        try {
            if (user.changed()) {
                user.attach(dsl.configuration());
                user.update();
            }
        } catch (DataIntegrityViolationException e) {
            throw IdentityInput.duplicate(e);
        }
        return user;
    }

    /// Deletes the user with its accounts, sessions and relationships (foreign keys cascade). True if it existed.
    boolean delete(UUID id) {
        return dsl.deleteFrom(USER).where(USER.ID.eq(id)).execute() > 0;
    }
}
