package com.heldertheking.exochat.api.messaging;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.id.AbstractUuidSubResourceName;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/// Resource name of a participant of a direct or group direct channel; references its account:
/// `channels/{channel}/participants/{participant}`.
/// `.../participants/-` is the wildcard name; a wildcard parent requires the wildcard here.
public final class ChannelParticipantName extends AbstractUuidSubResourceName<ChannelName, UUID> {
    private static final PathTemplate TEMPLATE = PathTemplate.create("channels/{channel}/participants/{participant}");
    private static final String VARIABLE = "participant";

    private ChannelParticipantName(ChannelName parent, @Nullable UUID id) {
        super(parent, id);
    }

    /// Name with the given parent and id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static ChannelParticipantName of(ChannelName parent, UUID id) {
        return new ChannelParticipantName(parent, id);
    }

    /// The wildcard name `.../participants/-` below the given parent.
    public static ChannelParticipantName wildcard(ChannelName parent) {
        return new ChannelParticipantName(parent, null);
    }

    /// Parses a name of the form `channels/{channel}/participants/{uuidv7}`; `-` is allowed for the last id.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static ChannelParticipantName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, ChannelName::fromName, ChannelParticipantName::new);
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
