package com.heldertheking.exochat.app.grpc;

import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.SessionName;
import com.heldertheking.exochat.api.identity.UserName;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/// Parses the resource names of a request. A name that is malformed or a wildcard is an INVALID_ARGUMENT, requests
/// address exactly one resource.
public final class NameParsing {
    private NameParsing() {}

    public static UserName user(String field, String name) {
        try {
            var parsed = UserName.fromName(name);
            return notWildcard(field, name, parsed.isWildcard(), parsed);
        } catch (IllegalArgumentException e) {
            throw invalid(field, name);
        }
    }

    public static SessionName session(String field, String name) {
        try {
            var parsed = SessionName.fromName(name);
            return notWildcard(field, name, parsed.isWildcard(), parsed);
        } catch (IllegalArgumentException e) {
            throw invalid(field, name);
        }
    }

    public static AccountName account(String field, String name) {
        try {
            var parsed = AccountName.fromName(name);
            return notWildcard(field, name, parsed.isWildcard(), parsed);
        } catch (IllegalArgumentException e) {
            throw invalid(field, name);
        }
    }

    private static <T> T notWildcard(String field, String name, boolean wildcard, T parsed) {
        if (wildcard) {
            throw invalid(field, name);
        }
        return parsed;
    }

    private static StatusRuntimeException invalid(String field, String name) {
        return Status.INVALID_ARGUMENT
                .withDescription("invalid %s '%s'".formatted(field, name))
                .asRuntimeException();
    }
}
