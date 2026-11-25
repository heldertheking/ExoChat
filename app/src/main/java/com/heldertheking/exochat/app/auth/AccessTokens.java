package com.heldertheking.exochat.app.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.heldertheking.exochat.api.id.UuidV7;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/// Issues the access tokens: ES256 JWTs with the claims `sub` (user id), `sid` (session id), optional
/// `acc` (active account id), `iss`, `iat`, `exp` and `jti` (ADR-012). The ids are the UUIDs of the resource names
/// (`users/{user}`, ...), not the names themselves. The tokens are verified by the decoder of the security package.
@Component
public class AccessTokens {
    private final AuthProperties properties;
    private final SigningKeys keys;
    private final Clock clock;

    public AccessTokens(AuthProperties properties, SigningKeys keys, Clock clock) {
        this.properties = properties;
        this.keys = keys;
        this.clock = clock;
    }

    /// A signed access token for the session and its active account.
    public IssuedAccessToken issue(UUID userId, UUID sessionId, @Nullable UUID accountId) {
        var now = clock.instant();
        var expireTime = now.plus(properties.accessTokenTtl());
        var claims = new JWTClaimsSet.Builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .jwtID(UuidV7.next().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expireTime))
                .claim("sid", sessionId.toString());
        if (accountId != null) {
            claims.claim("acc", accountId.toString());
        }
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keys.keyId()).build(), claims.build());
        try {
            jwt.sign(new ECDSASigner(keys.privateKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("could not sign the access token", e);
        }
        return new IssuedAccessToken(jwt.serialize(), expireTime);
    }

    /// A freshly signed token and when it expires.
    public record IssuedAccessToken(String token, Instant expireTime) {}
}
