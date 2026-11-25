package com.heldertheking.exochat.app.auth;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.UserName;

import io.grpc.Context;
import io.grpc.Status;

/// Who is making the current gRPC call, taken from its verified access token by [AuthInterceptor]. Handlers of
/// authenticated methods read it with [#current()].
///
/// @param userId id of the user (`sub`)
/// @param sessionId id of the session the token belongs to (`sid`)
/// @param accountId id of the active account (`acc`), null if the session has none
public record Caller(UUID userId, UUID sessionId, @Nullable UUID accountId) {
    static final Context.Key<Caller> KEY = Context.key("exochat-caller");

    /// The caller of the current call.
    ///
    /// @throws io.grpc.StatusRuntimeException UNAUTHENTICATED outside an authenticated call
    public static Caller current() {
        var caller = KEY.get();
        if (caller == null) {
            throw Status.UNAUTHENTICATED
                    .withDescription("authentication required")
                    .asRuntimeException();
        }
        return caller;
    }

    public UserName userName() {
        return UserName.of(userId);
    }

    /// The active account, null if the session has none.
    public @Nullable AccountName accountName() {
        return accountId == null ? null : AccountName.of(userName(), accountId);
    }

    /// Checks that the name belongs to the caller's own user.
    ///
    /// @throws io.grpc.StatusRuntimeException PERMISSION_DENIED for the resources of another user
    public void requireUser(UserName user) {
        if (user.isWildcard() || !user.getPersistenceId().equals(userId)) {
            throw Status.PERMISSION_DENIED
                    .withDescription("only the resources of your own user are accessible")
                    .asRuntimeException();
        }
    }
}
