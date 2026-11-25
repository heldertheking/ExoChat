package com.heldertheking.exochat.api.identity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.id.AbstractUuidSubResourceName;

/// Resource name of a relationship of an account (friend, blocked account, pending request):
/// `users/{user}/accounts/{account}/relationships/{relationship}`.
/// `.../relationships/-` is the wildcard name; a wildcard parent requires the wildcard here.
public final class RelationshipName extends AbstractUuidSubResourceName<AccountName, UUID> {
    private static final PathTemplate TEMPLATE =
            PathTemplate.create("users/{user}/accounts/{account}/relationships/{relationship}");
    private static final String VARIABLE = "relationship";

    private RelationshipName(AccountName parent, @Nullable UUID id) {
        super(parent, id);
    }

    /// Name with the given parent and id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static RelationshipName of(AccountName parent, UUID id) {
        return new RelationshipName(parent, id);
    }

    /// The wildcard name `.../relationships/-` below the given parent.
    public static RelationshipName wildcard(AccountName parent) {
        return new RelationshipName(parent, null);
    }

    /// Parses a name of the form `users/{user}/accounts/{account}/relationships/{uuidv7}`; `-` is allowed for the last
    /// id.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static RelationshipName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, AccountName::fromName, RelationshipName::new);
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
