package com.heldertheking.exochat.app.security;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;

/// Turns a verified access token into the authentication of the call, with an [AuthenticatedUser] as principal.
/// The token carries no authorities, see [AuthenticatedUser].
class AccessTokenAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var issuedAt = jwt.getIssuedAt();
        var expiresAt = jwt.getExpiresAt();
        if (issuedAt == null || expiresAt == null) {
            throw new InvalidBearerTokenException("token has no iat or exp");
        }
        var attributes = new TokenAttributes(
                uuid(jwt.getSubject(), "sub"),
                uuid(jwt.getClaimAsString("sid"), "sid"),
                optionalUuid(jwt.getClaimAsString("acc")),
                issuedAt,
                expiresAt);
        var principal = new AuthenticatedUser(attributes, List.of());
        var credentials =
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, jwt.getTokenValue(), issuedAt, expiresAt);
        return new BearerTokenAuthentication(principal, credentials, principal.getAuthorities());
    }

    private static UUID uuid(@Nullable String value, String claim) {
        if (value == null) {
            throw new InvalidBearerTokenException("token has no " + claim);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("token has an invalid " + claim, e);
        }
    }

    private static @Nullable UUID optionalUuid(@Nullable String value) {
        return value == null ? null : uuid(value, "acc");
    }
}
