package com.heldertheking.exochat.app.auth;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// Settings of the authentication (ADR-012), prefix `exochat.auth`.
///
/// @param issuer `iss` claim of the access tokens
/// @param accessTokenTtl lifetime of an access token
/// @param refreshTokenIdleTtl a session expires this long after its last refresh
/// @param refreshTokenMaxTtl a session expires this long after the login, however often it is
///     refreshed
/// @param signingKeyId `kid` header of the access tokens, change it when the key changes
/// @param signingPrivateKey ES256 private key as PKCS#8 PEM. Blank: an ephemeral development key is generated
///     at startup (and a warning logged), all tokens become invalid when the application restarts
/// @param signingPublicKey the matching public key as X.509 (SubjectPublicKeyInfo) PEM, required with the
///     private key
@ConfigurationProperties("exochat.auth")
public record AuthProperties(
        @DefaultValue("exochat") String issuer,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshTokenIdleTtl,
        @DefaultValue("90d") Duration refreshTokenMaxTtl,
        @DefaultValue("dev") String signingKeyId,
        @Nullable String signingPrivateKey,
        @Nullable String signingPublicKey) {}
