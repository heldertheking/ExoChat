package com.heldertheking.exochat.app.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Typed claims of an access token (ADR-012).
///
/// @param userId    id of the user, the `sub` claim
/// @param sessionId id of the session the token belongs to, the `sid` claim
/// @param accountId id of the active account, the `acc` claim, null if the session has none
/// @param issuedAt  `iat`
/// @param expiresAt `exp`
public record TokenAttributes(
        UUID userId, UUID sessionId, @Nullable UUID accountId, Instant issuedAt, Instant expiresAt) {

    Map<String, Object> asMap() {
        var map = new HashMap<String, Object>();
        map.put("sub", userId.toString());
        map.put("sid", sessionId.toString());
        if (accountId != null) {
            map.put("acc", accountId.toString());
        }
        map.put("iat", issuedAt);
        map.put("exp", expiresAt);
        return map;
    }
}
