package com.heldertheking.exochat.api.id;

import com.google.api.pathtemplate.PathTemplate;
import com.google.api.pathtemplate.TemplatedResourceName;
import com.google.api.pathtemplate.ValidationException;
import com.google.common.collect.ImmutableMap;
import com.heldertheking.resourcename.ResourceName;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/// Base class for top-level resource names whose id is a version 7 UUID (see [UuidV7]), like `users/{user}`.
/// A missing id is the wildcard (`users/-`).
///
/// Subclasses are final, have a private constructor and offer `of(UUID)`, `wildcard()` and `fromName(String)`
/// factories; [#parse] does the parsing.
public abstract class AbstractUuidResourceName implements ResourceName<UUID> {
    private final Optional<UUID> id;

    protected AbstractUuidResourceName(@Nullable UUID id) {
        this.id = Optional.ofNullable(validate(id));
    }

    /// The path template of this name, for example `users/{user}`.
    protected abstract PathTemplate pathTemplate();

    /// Name of the template variable that holds the id, for example `user`.
    protected abstract String variableName();

    /// Checks that the id is a UUIDv7.
    ///
    /// @throws IllegalArgumentException if it is not
    static @Nullable UUID validate(@Nullable UUID id) {
        if (id != null && !UuidV7.isV7(id)) {
            throw new IllegalArgumentException("id must be a UUIDv7, got " + id);
        }
        return id;
    }

    /// Reads the id of the template variable: null for the wildcard, otherwise a canonical UUIDv7.
    ///
    /// @throws IllegalArgumentException if the value is not a canonical UUIDv7
    static @Nullable UUID parseId(String value) {
        return MATCH_ALL.equals(value) ? null : UuidV7.parse(value);
    }

    /// Parses `name` against the template and creates the name object with the factory.
    ///
    /// @throws ValidationException (an [IllegalArgumentException]) if the string does not match
    protected static <N extends AbstractUuidResourceName> N parse(
            PathTemplate template, String variable, String name, Function<@Nullable UUID, N> factory) {
        var parsed = TemplatedResourceName.create(template, name);
        var value = Objects.requireNonNull(parsed.get(variable));
        try {
            return factory.apply(parseId(value));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("'%s' is not a valid resource name: %s", name, e.getMessage());
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
        return ImmutableMap.of(variableName(), id.map(UUID::toString).orElse(MATCH_ALL));
    }

    @Override
    public String toString() {
        return pathTemplate().instantiate(getTemplateVariables());
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof AbstractUuidResourceName that && getClass() == that.getClass() && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), id);
    }
}
