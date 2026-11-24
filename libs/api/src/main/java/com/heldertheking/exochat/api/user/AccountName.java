package com.heldertheking.exochat.api.user;

import com.google.api.pathtemplate.PathTemplate;
import com.heldertheking.exochat.api.id.AbstractUuidSubResourceName;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/// Resource name of the social persona of a user (handle, avatar, status):
/// `users/{user}/accounts/{account}`.
/// `.../accounts/-` is the wildcard name; a wildcard parent requires the wildcard here.
public final class AccountName extends AbstractUuidSubResourceName<UserName, UUID> {
    private static final PathTemplate TEMPLATE = PathTemplate.create("users/{user}/accounts/{account}");
    private static final String VARIABLE = "account";

    private AccountName(UserName parent, @Nullable UUID id) {
        super(parent, id);
    }

    /// Name with the given parent and id.
    ///
    /// @throws IllegalArgumentException if the id is not a version 7 UUID
    public static AccountName of(UserName parent, UUID id) {
        return new AccountName(parent, id);
    }

    /// The wildcard name `.../accounts/-` below the given parent.
    public static AccountName wildcard(UserName parent) {
        return new AccountName(parent, null);
    }

    /// Parses a name of the form `users/{user}/accounts/{uuidv7}`; `-` is allowed for the last id.
    ///
    /// @throws com.google.api.pathtemplate.ValidationException (an [IllegalArgumentException]) if the string is not
    ///         a valid name
    public static AccountName fromName(String name) {
        return parse(TEMPLATE, VARIABLE, name, UserName::fromName, AccountName::new);
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
