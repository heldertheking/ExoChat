package com.heldertheking.exochat.app.auth;

import java.util.Objects;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/// Hashes passwords with Argon2id (ADR-012): 19 MiB of memory, 2 iterations, 1 lane, which is the OWASP minimum.
/// The result is the self describing encoded form (parameters, salt and hash), so the parameters can be raised later
/// and old hashes keep verifying.
@Component
public class PasswordHasher {
    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 128;

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);

    /// A hash of a password nobody knows, verified when a login names an unknown email so that the response time does
    /// not reveal whether the email exists.
    private final String dummyHash = hash("dummy password for constant time checks");

    /// The encoded Argon2id hash of the password.
    public String hash(String password) {
        return Objects.requireNonNull(encoder.encode(password), "Argon2 returned no hash");
    }

    /// True if the password matches the encoded hash.
    public boolean matches(String password, String encodedHash) {
        return encoder.matches(password, encodedHash);
    }

    /// Spends the time of a verification without any result, see `dummyHash`.
    public void spendVerificationTime(String password) {
        encoder.matches(password, dummyHash);
    }

    /// Whether the password satisfies the policy: 8 to 128 characters, no composition rules.
    /// TODO: reject passwords known from breaches (k-anonymity range lookup).
    public static boolean isAcceptable(String password) {
        int length = password.codePointCount(0, password.length());
        return length >= MIN_LENGTH && length <= MAX_LENGTH;
    }
}
