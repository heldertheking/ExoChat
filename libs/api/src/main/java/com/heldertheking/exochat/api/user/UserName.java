package com.heldertheking.exochat.api.user;

import com.google.api.pathtemplate.PathTemplate;
import com.google.api.pathtemplate.TemplatedResourceName;
import com.google.api.pathtemplate.ValidationException;
import com.google.common.collect.ImmutableMap;
import com.heldertheking.exochat.api.id.UuidV7;
import com.heldertheking.resourcename.ResourceName;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/// Resource name of a user: `users/{user}`, where `{user}` is the lowercase canonical form of a version 7 UUID
/// (see [UuidV7]). The persistence id is the `user.id` column. `users/-` is the wildcard name matching every user.
public final class UserName implements ResourceName<UUID> {
    private static final PathTemplate TEMPLATE = PathTemplate.create("users/{user}");
    private static final String VARIABLE = "user";

    private final Optional<UUID> id;

    private UserName(@Nullable UUID id) {
        if (id != null && !UuidV7.isV7(id)) {
            throw new IllegalArgumentException("user id must be a UUIDv7, got " + id);
        }
        this.id = Optional.ofNullable(id);
    }

    /// Name of the user with the given persistence id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static UserName of(UUID id) {
        return new UserName(Objects.requireNonNull(id));
    }

    /// The wildcard name `users/-`.
    public static UserName wildcard() {
        return new UserName(null);
    }

    /// Parses `users/{uuidv7}` or `users/-`.
    ///
    /// @throws ValidationException (an [IllegalArgumentException]) if the string is not a user name
    public static UserName fromName(String name) {
        var parsed = TemplatedResourceName.create(TEMPLATE, name);
        var value = Objects.requireNonNull(parsed.get(VARIABLE));
        if (MATCH_ALL.equals(value)) {
            return wildcard();
        }
        try {
            return of(UuidV7.parse(value));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("'%s' is not a valid user name: %s", name, e.getMessage());
        }
    }

    @Override
    public UUID getPersistenceId() {
        return id.orElseThrow(() -> new IllegalStateException(
                "can not get persistence id on wildcard name %s".formatted(this)));
    }

    @Override
    public boolean isWildcard() {
        return id.isEmpty();
    }

    @Override
    public ImmutableMap<String, String> getTemplateVariables() {
        return ImmutableMap.of(VARIABLE, id.map(UUID::toString).orElse(MATCH_ALL));
    }

    @Override
    public String toString() {
        return TEMPLATE.instantiate(getTemplateVariables());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UserName that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
