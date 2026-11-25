package com.heldertheking.exochat.app.grpc.error;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import com.heldertheking.quela.FieldParseException;
import com.heldertheking.quela.FieldResolutionException;
import com.heldertheking.quela.orderby.OrderByParseException;
import com.heldertheking.quela.pagination.InvalidPageTokenException;
import com.heldertheking.quela.search.QueryParseException;
import com.heldertheking.quela.search.UnsupportedOperatorException;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

/// Translates the exceptions a handler throws into gRPC statuses, in the order of the `@Order` values: first the
/// statuses the code chose itself, then security, then the list query errors, finally a log of everything unknown
/// (which Spring gRPC answers with its default). Services and permission checkers just throw.
@Configuration
class GrpcErrorHandling {

    @Bean
    StatusExceptionHandler statusExceptionHandler() {
        return new StatusExceptionHandler();
    }

    @Bean
    SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler();
    }

    @Bean
    QueryExceptionHandler queryExceptionHandler() {
        return new QueryExceptionHandler();
    }

    @Bean
    FallbackLoggingExceptionHandler fallbackLoggingExceptionHandler() {
        return new FallbackLoggingExceptionHandler();
    }

    /// A [StatusRuntimeException] or [StatusException] thrown by the code is sent as it is.
    @Order(-20)
    static class StatusExceptionHandler implements GrpcExceptionHandler {
        @Override
        public @Nullable StatusException handleException(Throwable exception) {
            return switch (exception) {
                case StatusRuntimeException e -> new StatusException(e.getStatus(), e.getTrailers());
                case StatusException e -> e;
                default -> null;
            };
        }
    }

    /// [AccessDeniedException] is PERMISSION_DENIED, any other [AuthenticationException] UNAUTHENTICATED. The reason
    /// stays in the log, the caller learns nothing about what it may not do.
    @Order(-10)
    static class SecurityExceptionHandler implements GrpcExceptionHandler {
        private static final Logger LOG = LoggerFactory.getLogger(SecurityExceptionHandler.class);

        @Override
        public @Nullable StatusException handleException(Throwable exception) {
            return switch (exception) {
                case AccessDeniedException e -> {
                    LOG.warn("access denied: {}", e.getMessage());
                    yield Status.PERMISSION_DENIED
                            .withDescription("access denied")
                            .asException();
                }
                case AuthenticationException e ->
                    Status.UNAUTHENTICATED
                            .withDescription("authentication failed")
                            .asException();
                default -> null;
            };
        }
    }

    /// Invalid `filter`, `order_by` or `page_token` of a list request (quela) are INVALID_ARGUMENT.
    @Order(0)
    static class QueryExceptionHandler implements GrpcExceptionHandler {
        @Override
        public @Nullable StatusException handleException(Throwable exception) {
            if (exception instanceof QueryParseException
                    || exception instanceof OrderByParseException
                    || exception instanceof FieldResolutionException
                    || exception instanceof FieldParseException
                    || exception instanceof UnsupportedOperatorException
                    || exception instanceof InvalidPageTokenException) {
                return Status.INVALID_ARGUMENT
                        .withDescription(exception.getMessage())
                        .asException();
            }
            return null;
        }
    }

    /// Logs every exception no other handler translated, once, and leaves the response to Spring gRPC.
    @Order(Ordered.LOWEST_PRECEDENCE)
    static class FallbackLoggingExceptionHandler implements GrpcExceptionHandler {
        private static final Logger LOG = LoggerFactory.getLogger(FallbackLoggingExceptionHandler.class);

        @Override
        public @Nullable StatusException handleException(Throwable exception) {
            LOG.error("unhandled exception in gRPC service", exception);
            return null;
        }
    }
}
