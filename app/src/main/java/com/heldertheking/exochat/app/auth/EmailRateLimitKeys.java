package com.heldertheking.exochat.app.auth;

import java.util.Locale;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.heldertheking.exochat.app.ratelimit.RequestKeyExtractor;
import com.heldertheking.exochat.auth.v1alpha1.AuthenticateUserRequest;
import com.heldertheking.exochat.auth.v1alpha1.RegisterUserRequest;

/// The email address of a login or registration as second rate limit key (ADR-014), so that the password of one
/// account cannot be guessed from many addresses. Lower case and stripped, like the address is stored.
@Configuration
class EmailRateLimitKeys {

    @Bean
    RequestKeyExtractor<AuthenticateUserRequest> authenticateEmailKey() {
        return new Email<>(AuthenticateUserRequest.class, AuthenticateUserRequest::getEmail);
    }

    @Bean
    RequestKeyExtractor<RegisterUserRequest> registerEmailKey() {
        return new Email<>(RegisterUserRequest.class, RegisterUserRequest::getEmail);
    }

    private record Email<T>(Class<T> requestType, Function<T, String> email) implements RequestKeyExtractor<T> {
        @Override
        public @Nullable String extract(T request) {
            var value = email.apply(request).strip().toLowerCase(Locale.ROOT);
            return value.isEmpty() ? null : value;
        }
    }
}
