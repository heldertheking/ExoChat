package com.heldertheking.exochat.app.health;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.heldertheking.exochat.app.security.GrpcAuthorizationCustomizer;
import com.heldertheking.exochat.health.v1alpha1.HealthServiceGrpc;

/// The health check is open so that probes need no credentials.
@Configuration
class HealthSecurityConfiguration {

    @Bean
    GrpcAuthorizationCustomizer healthAuthorization() {
        return requests -> requests.methods(HealthServiceGrpc.getCheckMethod().getFullMethodName())
                .permitAll();
    }
}
