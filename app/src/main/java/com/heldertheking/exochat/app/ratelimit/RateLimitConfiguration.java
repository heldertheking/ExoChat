package com.heldertheking.exochat.app.ratelimit;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;

import io.grpc.BindableService;

/// Wires the rate limiting (ADR-014). The policies are resolved and checked here, so a typo in an annotation or in
/// the configuration stops the application at startup.
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfiguration {

    /// Bounded counters in memory. Define your own [RateLimitStore] bean (Valkey, ...) to replace it.
    @Bean
    @ConditionalOnMissingBean(RateLimitStore.class)
    RateLimitStore rateLimitStore(Clock clock, RateLimitProperties properties) {
        return new InMemoryRateLimitStore(clock, properties.maxKeys());
    }

    @Bean
    @GlobalServerInterceptor
    RateLimitInterceptor rateLimitInterceptor(
            RateLimitProperties properties,
            RateLimitStore store,
            List<BindableService> services,
            List<RequestKeyExtractor<?>> extractors) {
        var policies = RateLimitPolicies.resolve(properties);
        var byMethod = RateLimitPolicies.bind(services, policies);
        var byRequestType = new HashMap<Class<?>, RequestKeyExtractor<?>>();
        extractors.forEach(extractor -> byRequestType.put(extractor.requestType(), extractor));
        return new RateLimitInterceptor(
                properties.enabled(),
                properties.failOpen(),
                byMethod,
                byRequestType,
                store,
                new ClientAddresses(properties.trustedProxyHeader()));
    }
}
