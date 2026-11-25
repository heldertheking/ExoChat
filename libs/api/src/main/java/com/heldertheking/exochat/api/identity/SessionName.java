package com.heldertheking.exochat.api.identity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.id.AbstractUuidSubResourceName;

/// Resource name of a session of a user, one per logged in device (refresh token family, active account):
/// `users/{user}/sessions/{session}`.
/// `.../sessions/-` is the wildcard name; a wildcard parent requires the wildcard here.
public final class SessionName extends AbstractUuidSubResourceName<UserName, UUID> {
    private static final PathTemplate TEMPLATE = PathTemplate.create("users/{user}/sessions/{session}");
    private static final String VARIABLE = "session";

    private SessionName(UserName parent, @Nullable UUID id) {
        super(parent, id);
    }

    /// Name with the given parent and id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static SessionName of(UserName parent, UUID id) {
        return new SessionName(parent, id);
    }

    /// The wildcard name `.../sessions/-` below the given parent.
    public static SessionName wildcard(UserName parent) {
        return new SessionName(parent, null);
    }

    /// Parses a name of the form `users/{user}/sessions/{uuidv7}`; `-` is allowed for the last id.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static SessionName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, UserName::fromName, SessionName::new);
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
