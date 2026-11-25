package com.heldertheking.exochat.api.messaging;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.community.ServerName;
import com.heldertheking.exochat.api.id.AbstractUuidSubResourceName;

/// Alias name of a server channel: `servers/{server}/channels/{channel}`. It points at the same channel as the
/// canonical [ChannelName] (`channels/{channel}`), the ids are identical; use [#toChannelName()] to get the
/// canonical name. `.../channels/-` is the wildcard name; a wildcard parent requires the wildcard here.
public final class ServerChannelName extends AbstractUuidSubResourceName<ServerName, UUID> {
    private static final PathTemplate TEMPLATE = PathTemplate.create("servers/{server}/channels/{channel}");
    private static final String VARIABLE = "channel";

    private ServerChannelName(ServerName parent, @Nullable UUID id) {
        super(parent, id);
    }

    /// Name with the given parent and id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static ServerChannelName of(ServerName parent, UUID id) {
        return new ServerChannelName(parent, id);
    }

    /// The wildcard name `.../channels/-` below the given parent.
    public static ServerChannelName wildcard(ServerName parent) {
        return new ServerChannelName(parent, null);
    }

    /// Parses a name of the form `servers/{server}/channels/{uuidv7}`; `-` is allowed for the last id.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static ServerChannelName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, ServerName::fromName, ServerChannelName::new);
    }

    /// The canonical name of the same channel (`channels/{channel}`); the wildcard stays a wildcard.
    public ChannelName toChannelName() {
        return isWildcard() ? ChannelName.wildcard() : ChannelName.of(getPersistenceId());
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
