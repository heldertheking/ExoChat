package com.heldertheking.exochat.api.messaging;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.id.AbstractUuidSubResourceName;

/// Resource name of a message; the only way to address a message:
/// `channels/{channel}/messages/{message}`.
/// `.../messages/-` is the wildcard name; a wildcard parent requires the wildcard here.
public final class MessageName extends AbstractUuidSubResourceName<ChannelName, UUID> {
    private static final PathTemplate TEMPLATE = PathTemplate.create("channels/{channel}/messages/{message}");
    private static final String VARIABLE = "message";

    private MessageName(ChannelName parent, @Nullable UUID id) {
        super(parent, id);
    }

    /// Name with the given parent and id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static MessageName of(ChannelName parent, UUID id) {
        return new MessageName(parent, id);
    }

    /// The wildcard name `.../messages/-` below the given parent.
    public static MessageName wildcard(ChannelName parent) {
        return new MessageName(parent, null);
    }

    /// Parses a name of the form `channels/{channel}/messages/{uuidv7}`; `-` is allowed for the last id.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static MessageName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, ChannelName::fromName, MessageName::new);
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
