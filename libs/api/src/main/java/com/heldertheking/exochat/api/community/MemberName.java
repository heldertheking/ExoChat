package com.heldertheking.exochat.api.community;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.id.AbstractUuidSubResourceName;

/// Resource name of a member of a server; references its account by `users/{user}/accounts/{account}`:
/// `servers/{server}/members/{member}`.
/// `.../members/-` is the wildcard name; a wildcard parent requires the wildcard here.
public final class MemberName extends AbstractUuidSubResourceName<ServerName, UUID> {
    private static final PathTemplate TEMPLATE = PathTemplate.create("servers/{server}/members/{member}");
    private static final String VARIABLE = "member";

    private MemberName(ServerName parent, @Nullable UUID id) {
        super(parent, id);
    }

    /// Name with the given parent and id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static MemberName of(ServerName parent, UUID id) {
        return new MemberName(parent, id);
    }

    /// The wildcard name `.../members/-` below the given parent.
    public static MemberName wildcard(ServerName parent) {
        return new MemberName(parent, null);
    }

    /// Parses a name of the form `servers/{server}/members/{uuidv7}`; `-` is allowed for the last id.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static MemberName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, ServerName::fromName, MemberName::new);
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
