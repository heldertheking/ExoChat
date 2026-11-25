package com.heldertheking.exochat.api.community;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.id.AbstractUuidResourceName;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/// Resource name of a server (community): `servers/{server}`, where `{server}` is the lowercase canonical form of a
/// version 7 UUID. `servers/-` is the wildcard name matching all of them.
public final class ServerName extends AbstractUuidResourceName {
    private static final PathTemplate TEMPLATE = PathTemplate.create("servers/{server}");
    private static final String VARIABLE = "server";

    private ServerName(@Nullable UUID id) {
        super(id);
    }

    /// Name with the given id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static ServerName of(UUID id) {
        return new ServerName(id);
    }

    /// The wildcard name `servers/-`.
    public static ServerName wildcard() {
        return new ServerName(null);
    }

    /// Parses `servers/{uuidv7}` or `servers/-`.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static ServerName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, ServerName::new);
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
