package com.heldertheking.exochat.api.community;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.id.AbstractUuidSubResourceName;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/// Resource name of an invite to a server:
/// `servers/{server}/invites/{invite}`.
/// `.../invites/-` is the wildcard name; a wildcard parent requires the wildcard here.
public final class InviteName extends AbstractUuidSubResourceName<ServerName, UUID> {
    private static final PathTemplate TEMPLATE = PathTemplate.create("servers/{server}/invites/{invite}");
    private static final String VARIABLE = "invite";

    private InviteName(ServerName parent, @Nullable UUID id) {
        super(parent, id);
    }

    /// Name with the given parent and id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static InviteName of(ServerName parent, UUID id) {
        return new InviteName(parent, id);
    }

    /// The wildcard name `.../invites/-` below the given parent.
    public static InviteName wildcard(ServerName parent) {
        return new InviteName(parent, null);
    }

    /// Parses a name of the form `servers/{server}/invites/{uuidv7}`; `-` is allowed for the last id.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static InviteName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, ServerName::fromName, InviteName::new);
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
