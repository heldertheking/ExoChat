package com.heldertheking.exochat.api.messaging;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.id.AbstractUuidSubResourceName;

/// Resource name of a permission overwrite of a server channel:
/// `channels/{channel}/overwrites/{overwrite}`.
/// `.../overwrites/-` is the wildcard name; a wildcard parent requires the wildcard here.
public final class ChannelOverwriteName extends AbstractUuidSubResourceName<ChannelName, UUID> {
    private static final PathTemplate TEMPLATE = PathTemplate.create("channels/{channel}/overwrites/{overwrite}");
    private static final String VARIABLE = "overwrite";

    private ChannelOverwriteName(ChannelName parent, @Nullable UUID id) {
        super(parent, id);
    }

    /// Name with the given parent and id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static ChannelOverwriteName of(ChannelName parent, UUID id) {
        return new ChannelOverwriteName(parent, id);
    }

    /// The wildcard name `.../overwrites/-` below the given parent.
    public static ChannelOverwriteName wildcard(ChannelName parent) {
        return new ChannelOverwriteName(parent, null);
    }

    /// Parses a name of the form `channels/{channel}/overwrites/{uuidv7}`; `-` is allowed for the last id.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static ChannelOverwriteName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, ChannelName::fromName, ChannelOverwriteName::new);
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
