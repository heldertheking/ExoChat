package com.heldertheking.exochat.api.id;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.google.api.pathtemplate.PathTemplate;
import com.google.api.pathtemplate.TemplatedResourceName;
import com.google.api.pathtemplate.ValidationException;
import com.heldertheking.resourcename.AbstractSubResourceName;
import com.heldertheking.resourcename.ResourceName;

/// Base class for sub resource names whose id is a version 7 UUID (see [UuidV7]), like
/// `users/{user}/accounts/{account}`. A missing id is the wildcard (`.../accounts/-`); a wildcard parent
/// requires a wildcard child (`users/-/accounts/-`).
///
/// Subclasses are final, have a private constructor and offer `of(parent, UUID)`, `wildcard(parent)` and
/// `fromName(String)` factories; [#parse] does the parsing.
///
/// @param <P>   parent resource name type
/// @param <PID> persistence id type of the parent
public abstract class AbstractUuidSubResourceName<P extends ResourceName<PID>, PID>
        extends AbstractSubResourceName<P, PID, UUID> {

    protected AbstractUuidSubResourceName(P parent, @Nullable UUID id) {
        super(parent, AbstractUuidResourceName.validate(id));
    }

    /// Parses `name` against the template and creates the name object. The parent is parsed from the
    /// leading part of the name with `parentParser` (normally the `fromName` of the parent type).
    ///
    /// @throws ValidationException (an [IllegalArgumentException]) if the string does not match
    protected static <P extends ResourceName<PID>, PID, N extends AbstractUuidSubResourceName<P, PID>> N parse(
            PathTemplate template,
            String variable,
            String name,
            Function<String, P> parentParser,
            BiFunction<P, @Nullable UUID, N> factory) {
        var parsed = TemplatedResourceName.create(template, name);
        var value = Objects.requireNonNull(parsed.get(variable));
        // name is "<parent>/<collection>/<id>", validated against the template above
        var parentEnd = name.lastIndexOf('/', name.lastIndexOf('/') - 1);
        var parent = parentParser.apply(name.substring(0, parentEnd));
        try {
            return factory.apply(parent, AbstractUuidResourceName.parseId(value));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("'%s' is not a valid resource name: %s", name, e.getMessage());
        }
    }
}
