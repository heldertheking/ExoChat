package com.heldertheking.exochat.app.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/// Opaque refresh tokens (ADR-012): 256 random bits, base64url encoded. Only the SHA-256 hash is stored; a hash of a
/// high entropy random value needs no salt.
final class RefreshTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokens() {}

    /// A new random token with its hash.
    static Generated generate() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Generated(token, hash(token));
    }

    /// The hash that is stored for the token.
    static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }

    /// A token and its hash.
    record Generated(String token, byte[] hash) {}
}
