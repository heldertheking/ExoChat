package com.heldertheking.exochat.app.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

import com.heldertheking.exochat.api.identity.UserName;

/// The principal of every call with a valid access token. It only says who is calling (user, session, active
/// account). What the caller may do is decided by the `PermissionEvaluator` at the time of the call, never taken
/// from the token, so that a change of standing or rules applies at once.
public class AuthenticatedUser implements OAuth2AuthenticatedPrincipal {
    private final TokenAttributes attrs;
    private final Set<? extends GrantedAuthority> authorities;

    public AuthenticatedUser(TokenAttributes attrs, Collection<? extends GrantedAuthority> authorities) {
        this.attrs = attrs;
        this.authorities = Collections.unmodifiableSet(new LinkedHashSet<>(authorities));
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attrs.asMap();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /// The resource name of the user, `users/{user}`.
    @Override
    public String getName() {
        return UserName.of(attrs.userId()).toString();
    }

    public TokenAttributes getAttrs() {
        return attrs;
    }
}
