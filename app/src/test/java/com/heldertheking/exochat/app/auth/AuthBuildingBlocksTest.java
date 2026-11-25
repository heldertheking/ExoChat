package com.heldertheking.exochat.app.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.heldertheking.exochat.api.id.UuidV7;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;

class AuthBuildingBlocksTest {
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private static AuthProperties properties() {
        return new AuthProperties(
                "exochat", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofDays(90), "test", null, null);
    }

    private static AccessTokens tokens(SigningKeys keys, Instant now) {
        return new AccessTokens(properties(), keys, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void passwordHasher_should_hashAndVerify() {
        var hasher = new PasswordHasher();

        var hash = hasher.hash("correct horse battery staple");

        assertThat(hash).startsWith("$argon2id$").doesNotContain("correct horse");
        assertThat(hasher.matches("correct horse battery staple", hash)).isTrue();
        assertThat(hasher.matches("wrong", hash)).isFalse();
        assertThat(hasher.hash("correct horse battery staple")).as("salted").isNotEqualTo(hash);
    }

    @Test
    void passwordHasher_should_enforceTheLengthPolicy() {
        assertThat(PasswordHasher.isAcceptable("1234567")).isFalse();
        assertThat(PasswordHasher.isAcceptable("12345678")).isTrue();
        assertThat(PasswordHasher.isAcceptable("a".repeat(128))).isTrue();
        assertThat(PasswordHasher.isAcceptable("a".repeat(129))).isFalse();
    }

    @Test
    void refreshTokens_should_beRandomAndHashStably() {
        var a = RefreshTokens.generate();
        var b = RefreshTokens.generate();

        assertThat(a.token()).isNotEqualTo(b.token()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(a.hash()).hasSize(32).isEqualTo(RefreshTokens.hash(a.token()));
        assertThat(a.hash()).isNotEqualTo(b.hash());
    }

    @Test
    void accessToken_should_roundTrip() {
        var keys = new SigningKeys(properties());
        var user = UuidV7.next();
        var session = UuidV7.next();
        var account = UuidV7.next();
        var accessTokens = tokens(keys, NOW);

        var issued = accessTokens.issue(user, session, account);
        var claims = accessTokens.verify(issued.token());

        assertThat(claims.userId()).isEqualTo(user);
        assertThat(claims.sessionId()).isEqualTo(session);
        assertThat(claims.accountId()).isEqualTo(account);
        assertThat(claims.expireTime()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(issued.expireTime()).isEqualTo(claims.expireTime());
    }

    @Test
    void accessToken_should_allowNoActiveAccount() {
        var keys = new SigningKeys(properties());
        var accessTokens = tokens(keys, NOW);

        var claims = accessTokens.verify(
                accessTokens.issue(UuidV7.next(), UuidV7.next(), null).token());

        assertThat(claims.accountId()).isNull();
    }

    @Test
    void accessToken_should_beRejected_when_expired() {
        var keys = new SigningKeys(properties());
        var token = tokens(keys, NOW).issue(UuidV7.next(), UuidV7.next(), null).token();

        var later = tokens(keys, NOW.plus(Duration.ofMinutes(15)));

        assertThatThrownBy(() -> later.verify(token)).isInstanceOf(AccessTokens.InvalidAccessTokenException.class);
        assertThat(tokens(keys, NOW.plus(Duration.ofMinutes(14))).verify(token)).isNotNull();
    }

    @Test
    void accessToken_should_beRejected_when_signedByAnotherKey() {
        var token = tokens(new SigningKeys(properties()), NOW)
                .issue(UuidV7.next(), UuidV7.next(), null)
                .token();
        var otherKeys = new SigningKeys(properties());

        assertThatThrownBy(() -> tokens(otherKeys, NOW).verify(token))
                .isInstanceOf(AccessTokens.InvalidAccessTokenException.class);
    }

    @Test
    void accessToken_should_beRejected_when_tamperedOrUnsigned() {
        var keys = new SigningKeys(properties());
        var accessTokens = tokens(keys, NOW);
        var token = accessTokens.issue(UuidV7.next(), UuidV7.next(), null).token();
        var parts = token.split("\\.");
        var forgedPayload = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(("{\"sub\":\"" + UUID.randomUUID() + "\",\"sid\":\"" + UUID.randomUUID()
                                + "\",\"iss\":\"exochat\",\"exp\":" + (NOW.getEpochSecond() + 600) + "}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var unsigned = new PlainJWT(new JWTClaimsSet.Builder()
                        .issuer("exochat")
                        .subject(UuidV7.next().toString())
                        .claim("sid", UuidV7.next().toString())
                        .expirationTime(java.util.Date.from(NOW.plusSeconds(600)))
                        .build())
                .serialize();

        assertThatThrownBy(() -> accessTokens.verify(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(AccessTokens.InvalidAccessTokenException.class);
        assertThatThrownBy(() -> accessTokens.verify(unsigned))
                .isInstanceOf(AccessTokens.InvalidAccessTokenException.class);
        assertThatThrownBy(() -> accessTokens.verify("not a token"))
                .isInstanceOf(AccessTokens.InvalidAccessTokenException.class);
    }

    @Test
    void signingKeys_should_readConfiguredPemKeys() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        var privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
        var publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());
        var configured = new AuthProperties(
                "exochat",
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                Duration.ofDays(90),
                "k1",
                privatePem,
                publicPem);

        var keys = new SigningKeys(configured);

        assertThat(keys.publicKey().getEncoded()).isEqualTo(pair.getPublic().getEncoded());
        assertThat(keys.keyId()).isEqualTo("k1");
        assertThatThrownBy(() -> new SigningKeys(new AuthProperties(
                        "exochat",
                        Duration.ofMinutes(15),
                        Duration.ofDays(30),
                        Duration.ofDays(90),
                        "k1",
                        privatePem,
                        null)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + java.util.Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
