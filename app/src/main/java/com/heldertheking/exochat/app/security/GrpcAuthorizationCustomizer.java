package com.heldertheking.exochat.app.security;

import org.springframework.grpc.server.security.RequestMapperConfigurer;

/// Extends the authorization rules of the gRPC server. Every call requires an authenticated caller unless a
/// customizer opens it, e.g. `requests.methods("my.Service/Login").permitAll()`. Customizers run before the final
/// "everything else must be authenticated" rule, so a domain declares the rules of its own methods next to its
/// service.
@FunctionalInterface
public interface GrpcAuthorizationCustomizer {
    void customize(RequestMapperConfigurer requests);
}
