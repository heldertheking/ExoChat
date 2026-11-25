package com.heldertheking.exochat.app.security;

import java.time.Clock;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.BearerTokenAuthenticationExtractor;
import org.springframework.grpc.server.security.GrpcSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

import com.heldertheking.exochat.app.auth.AuthProperties;
import com.heldertheking.exochat.app.auth.SigningKeys;

/// Authenticates every gRPC call with the bearer access token (ADR-012). The token is verified without a database
/// lookup. The calls that open themselves through a [GrpcAuthorizationCustomizer] are the only ones that work
/// without a token.
@Configuration
class SecurityConfiguration {

    @Bean
    JwtDecoder accessTokenDecoder(SigningKeys keys, AuthProperties properties, Clock clock) {
        return AccessTokenDecoders.create(keys, properties, clock);
    }

    /// Passed to [GrpcSecurity] explicitly, otherwise it assembles its own manager from the provider beans of the
    /// context and this one would not authenticate the calls.
    @Bean
    AuthenticationManager grpcAuthenticationManager(JwtDecoder accessTokenDecoder) {
        var provider = new JwtAuthenticationProvider(accessTokenDecoder);
        provider.setJwtAuthenticationConverter(new AccessTokenAuthenticationConverter());
        return new ProviderManager(provider);
    }

    @Bean
    @GlobalServerInterceptor
    AuthenticationProcessInterceptor grpcSecurityFilterChain(
            GrpcSecurity grpc,
            AuthenticationManager grpcAuthenticationManager,
            List<GrpcAuthorizationCustomizer> customizers)
            throws Exception {
        return grpc.authenticationManager(grpcAuthenticationManager)
                .authorizeRequests(requests -> {
                    customizers.forEach(customizer -> customizer.customize(requests));
                    requests.allRequests().authenticated();
                })
                .authenticationExtractor(new BearerTokenAuthenticationExtractor())
                .build();
    }
}
