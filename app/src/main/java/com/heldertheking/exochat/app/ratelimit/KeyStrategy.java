package com.heldertheking.exochat.app.ratelimit;

/// What the counter of a policy is keyed by.
public enum KeyStrategy {
    /// The address of the caller. The only possible key of the calls that need no login.
    PEER,
    /// The authenticated user. A call without one is rejected as unauthenticated.
    USER,
    /// One counter per user and address.
    PEER_AND_USER
}
