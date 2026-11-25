package com.heldertheking.exochat.api.identity;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.id.AbstractUuidResourceName;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/// Resource name of a user: `users/{user}`, where `{user}` is the lowercase canonical form of a
/// version 7 UUID. `users/-` is the wildcard name matching all of them.
public final class UserName extends AbstractUuidResourceName {
    private static final PathTemplate TEMPLATE = PathTemplate.create("users/{user}");
    private static final String VARIABLE = "user";

    private UserName(@Nullable UUID id) {
        super(id);
    }

    /// Name with the given id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static UserName of(UUID id) {
        return new UserName(id);
    }

    /// The wildcard name `users/-`.
    public static UserName wildcard() {
        return new UserName(null);
    }

    /// Parses `users/{uuidv7}` or `users/-`.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static UserName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, UserName::new);
    }

    @Override
    protected PathTemplate pathTemplate() {
        return TEMPLATE;
    }

    @Override
    protected String variableName() {
        return VARIABLE;
    }
}
