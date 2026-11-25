package com.heldertheking.exochat.app.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Rate limits an RPC (ADR-014). Put it on the method of a gRPC service implementation (a class extending a
/// generated `*ImplBase`) that handles the RPC. Methods without it are not limited.
///
/// The value names a policy under `exochat.ratelimit.policies`, or one of the built-in ones. A name that is
/// not configured makes the application fail at startup.
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimited {

    /// Name of the policy.
    String value();
}
