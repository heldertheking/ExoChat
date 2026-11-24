package com.heldertheking.exochat.api.channel;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.id.AbstractUuidSubResourceName;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/// Resource name of a permission overwrite of a server channel:
/// `channels/{channel}/overwrites/{overwrite}`.
/// `.../overwrites/-` is the wildcard name; a wildcard parent requires the wildcard here.
public final class OverwriteName extends AbstractUuidSubResourceName<ChannelName, UUID> {
    private static final PathTemplate TEMPLATE = PathTemplate.create("channels/{channel}/overwrites/{overwrite}");
    private static final String VARIABLE = "overwrite";

    private OverwriteName(ChannelName parent, @Nullable UUID id) {
        super(parent, id);
    }

    /// Name with the given parent and id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static OverwriteName of(ChannelName parent, UUID id) {
        return new OverwriteName(parent, id);
    }

    /// The wildcard name `.../overwrites/-` below the given parent.
    public static OverwriteName wildcard(ChannelName parent) {
        return new OverwriteName(parent, null);
    }

    /// Parses a name of the form `channels/{channel}/overwrites/{uuidv7}`; `-` is allowed for the last id.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static OverwriteName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, ChannelName::fromName, OverwriteName::new);
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
