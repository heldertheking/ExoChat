package com.heldertheking.exochat.app.security;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.heldertheking.exochat.app.auth.AuthProperties;
import com.heldertheking.exochat.app.auth.SigningKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

/// Builds the decoder that verifies our own ES256 access tokens. Spring's `NimbusJwtDecoder.withPublicKey` only
/// knows RSA keys, so the Nimbus processor is assembled here. Signature, `exp` (without clock skew, the issuer is
/// this very application) and `iss` are checked, anything else is up to the converter.
public final class AccessTokenDecoders {
    private AccessTokenDecoders() {}

    public static JwtDecoder create(SigningKeys keys, AuthProperties properties, Clock clock) {
        var key = new ECKey.Builder(Curve.P_256, keys.publicKey())
                .keyID(keys.keyId())
                .build();
        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, new ImmutableJWKSet<>(new JWKSet(key))));
        // the claims are validated by the validators of the decoder below
        processor.setJWTClaimsSetVerifier((claims, context) -> {});
        var decoder = new NimbusJwtDecoder(processor);
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(List.of(timestamps, new JwtIssuerValidator(properties.issuer()))));
        return decoder;
    }
}
