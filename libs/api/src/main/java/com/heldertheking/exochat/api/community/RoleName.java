package com.heldertheking.exochat.api.community;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.id.AbstractUuidSubResourceName;

/// Resource name of a role of a server (RBAC):
/// `servers/{server}/roles/{role}`.
/// `.../roles/-` is the wildcard name; a wildcard parent requires the wildcard here.
public final class RoleName extends AbstractUuidSubResourceName<ServerName, UUID> {
    private static final PathTemplate TEMPLATE = PathTemplate.create("servers/{server}/roles/{role}");
    private static final String VARIABLE = "role";

    private RoleName(ServerName parent, @Nullable UUID id) {
        super(parent, id);
    }

    /// Name with the given parent and id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static RoleName of(ServerName parent, UUID id) {
        return new RoleName(parent, id);
    }

    /// The wildcard name `.../roles/-` below the given parent.
    public static RoleName wildcard(ServerName parent) {
        return new RoleName(parent, null);
    }

    /// Parses a name of the form `servers/{server}/roles/{uuidv7}`; `-` is allowed for the last id.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static RoleName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, ServerName::fromName, RoleName::new);
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
