package com.heldertheking.exochat.app.ratelimit;

import org.jspecify.annotations.Nullable;

/// Extension point: derives a second rate limit key from the body of a request, for example the email address of
/// a login so that guessing the password of one account is limited whatever the address. Register a bean per
/// request type. It is used by the policies that set `request-key-limit`.
///
/// The key is hashed before it is counted or logged, so an implementation may return personal data, but it
/// should normalise it (case, whitespace) so that variants share one counter.
///
/// @param <T> the request message
public interface RequestKeyExtractor<T> {

    /// The request type this extractor reads.
    Class<T> requestType();

    /// The key of the request, null if it has none (then only the other keys of the policy apply).
    @Nullable String extract(T request);
}
