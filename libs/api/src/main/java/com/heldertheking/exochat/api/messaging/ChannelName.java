package com.heldertheking.exochat.api.messaging;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.community.ServerName;
import com.heldertheking.exochat.api.id.AbstractUuidResourceName;

/// Canonical resource name of a channel of any kind (direct, group or server): `channels/{channel}`, where
/// `{channel}` is the lowercase canonical form of a version 7 UUID. `channels/-` is the wildcard name matching all
/// of them. A server channel can also be addressed by its alias [ServerChannelName]
/// (`servers/{server}/channels/{channel}`); both name the same channel.
public final class ChannelName extends AbstractUuidResourceName {
    private static final PathTemplate TEMPLATE = PathTemplate.create("channels/{channel}");
    private static final String VARIABLE = "channel";

    private ChannelName(@Nullable UUID id) {
        super(id);
    }

    /// Name with the given id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static ChannelName of(UUID id) {
        return new ChannelName(id);
    }

    /// The wildcard name `channels/-`.
    public static ChannelName wildcard() {
        return new ChannelName(null);
    }

    /// Parses `channels/{uuidv7}` or `channels/-`.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static ChannelName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, ChannelName::new);
    }

    /// Parses either form, the canonical `channels/{uuidv7}` or the alias `servers/{server}/channels/{uuidv7}`, and
    /// returns the canonical name. The server of an alias is dropped; use [ServerChannelName#fromName] to keep it.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static ChannelName fromAnyName(String name) {
        return name.startsWith("servers/") ? ServerChannelName.fromName(name).toChannelName() : fromName(name);
    }

    /// The alias name of this channel below the given server.
    public ServerChannelName inServer(ServerName server) {
        return isWildcard() ? ServerChannelName.wildcard(server) : ServerChannelName.of(server, getPersistenceId());
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
