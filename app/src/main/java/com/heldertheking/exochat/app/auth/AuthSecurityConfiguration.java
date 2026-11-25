package com.heldertheking.exochat.app.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.heldertheking.exochat.app.security.GrpcAuthorizationCustomizer;
import com.heldertheking.exochat.auth.v1alpha1.AuthServiceGrpc;

/// The calls that get an access token cannot need one: registering, logging in and refreshing are open. Every other
/// call of the auth service requires authentication like all the others.
@Configuration
class AuthSecurityConfiguration {

    @Bean
    GrpcAuthorizationCustomizer authServiceAuthorization() {
        return requests -> {
            requests.methods(AuthServiceGrpc.getRegisterUserMethod().getFullMethodName())
                    .permitAll();
            requests.methods(AuthServiceGrpc.getAuthenticateUserMethod().getFullMethodName())
                    .permitAll();
            requests.methods(AuthServiceGrpc.getRefreshSessionMethod().getFullMethodName())
                    .permitAll();
        };
    }
}
