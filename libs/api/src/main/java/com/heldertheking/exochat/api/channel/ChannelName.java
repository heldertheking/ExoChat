package com.heldertheking.exochat.api.channel;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.id.AbstractUuidResourceName;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/// Resource name of a channel: direct, group direct, server text or server voice: `channels/{channel}`, where `{channel}` is the lowercase canonical form of a
/// version 7 UUID. `channels/-` is the wildcard name matching all of them.
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

    @Override
    protected PathTemplate pathTemplate() {
        return TEMPLATE;
    }

    @Override
    protected String variableName() {
        return VARIABLE;
    }
}
