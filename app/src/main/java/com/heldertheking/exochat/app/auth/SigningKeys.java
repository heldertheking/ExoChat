package com.heldertheking.exochat.app.auth;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/// The ES256 key pair that signs and verifies access tokens (ADR-012). It is read from the configuration; without
/// one an ephemeral key is generated so the application starts in development.
///
/// Generate a key with
/// `openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out private.pem` and
/// `openssl ec -in private.pem -pubout -out public.pem`.
@Component
public class SigningKeys {
    private static final Logger LOG = LoggerFactory.getLogger(SigningKeys.class);

    private final String keyId;
    private final ECPrivateKey privateKey;
    private final ECPublicKey publicKey;

    public SigningKeys(AuthProperties properties) {
        this.keyId = properties.signingKeyId();
        var privatePem = properties.signingPrivateKey();
        var publicPem = properties.signingPublicKey();
        try {
            if (privatePem == null || privatePem.isBlank()) {
                LOG.warn("exochat.auth.signing-private-key is not set: using an ephemeral development key,"
                        + " all access tokens become invalid when the application restarts");
                var generator = KeyPairGenerator.getInstance("EC");
                generator.initialize(new ECGenParameterSpec("secp256r1"));
                KeyPair pair = generator.generateKeyPair();
                this.privateKey = (ECPrivateKey) pair.getPrivate();
                this.publicKey = (ECPublicKey) pair.getPublic();
            } else if (publicPem == null || publicPem.isBlank()) {
                throw new IllegalStateException(
                        "exochat.auth.signing-public-key is required together with the private key");
            } else {
                var factory = KeyFactory.getInstance("EC");
                this.privateKey =
                        (ECPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(privatePem)));
                this.publicKey = (ECPublicKey) factory.generatePublic(new X509EncodedKeySpec(decodePem(publicPem)));
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("invalid access token signing key", e);
        }
    }

    public String keyId() {
        return keyId;
    }

    public ECPrivateKey privateKey() {
        return privateKey;
    }

    public ECPublicKey publicKey() {
        return publicKey;
    }

    private static byte[] decodePem(String pem) {
        var body = pem.replaceAll("-----(BEGIN|END)[A-Z ]*-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
