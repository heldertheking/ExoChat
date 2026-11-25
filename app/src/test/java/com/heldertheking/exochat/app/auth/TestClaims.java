package com.heldertheking.exochat.app.auth;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;

/// The claims of an access token as the tests read them.
record TestClaims(UUID userId, UUID sessionId, @Nullable UUID accountId, Instant expireTime) {

    static TestClaims of(Jwt jwt) {
        var account = jwt.getClaimAsString("acc");
        return new TestClaims(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString("sid")),
                account == null ? null : UUID.fromString(account),
                jwt.getExpiresAt());
    }
}
