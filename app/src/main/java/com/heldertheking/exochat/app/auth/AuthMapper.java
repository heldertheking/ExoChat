package com.heldertheking.exochat.app.auth;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.google.protobuf.Timestamp;
import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.SessionName;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.auth.v1alpha1.AuthTokens;
import com.heldertheking.exochat.db.schema.tables.records.AccountRecord;
import com.heldertheking.exochat.db.schema.tables.records.SessionRecord;
import com.heldertheking.exochat.db.schema.tables.records.UserRecord;
import com.heldertheking.exochat.identity.v1alpha1.Account;
import com.heldertheking.exochat.identity.v1alpha1.Session;
import com.heldertheking.exochat.identity.v1alpha1.User;

/// Converts database records to the protobuf messages. Database timestamps are UTC.
final class AuthMapper {
    private AuthMapper() {}

    static User user(UserRecord user) {
        var builder = User.newBuilder()
                .setName(UserName.of(user.getId()).toString())
                .setEmail(user.getEmail())
                .setCreateTime(timestamp(user.getCreateTime()));
        if (user.getPhoneNumber() != null) {
            builder.setPhoneNumber(user.getPhoneNumber());
        }
        return builder.build();
    }

    static Account account(AccountRecord account) {
        var builder = Account.newBuilder()
                .setName(accountName(account).toString())
                .setUsername(account.getUsername())
                .setDisplayName(account.getDisplayName())
                .setCreateTime(timestamp(account.getCreateTime()));
        if (account.getAvatarUrl() != null) {
            builder.setAvatarUrl(account.getAvatarUrl());
        }
        return builder.build();
    }

    /// @param current whether the session is the one the request was made with
    static Session session(SessionRecord session, boolean current) {
        var userName = UserName.of(session.getUserId());
        var builder = Session.newBuilder()
                .setName(SessionName.of(userName, session.getId()).toString())
                .setCurrent(current)
                .setCreateTime(timestamp(session.getCreateTime()))
                .setLastUsedTime(timestamp(session.getLastUsedTime()))
                .setExpireTime(timestamp(session.getExpireTime()));
        if (session.getDeviceLabel() != null) {
            builder.setDeviceLabel(session.getDeviceLabel());
        }
        if (session.getAccountId() != null) {
            builder.setAccount(AccountName.of(userName, session.getAccountId()).toString());
        }
        return builder.build();
    }

    static AuthTokens tokens(AuthenticationService.Authenticated authenticated) {
        return AuthTokens.newBuilder()
                .setAccessToken(authenticated.accessToken())
                .setAccessTokenExpireTime(timestamp(authenticated.accessTokenExpireTime()))
                .setRefreshToken(authenticated.refreshToken())
                .setRefreshTokenExpireTime(timestamp(authenticated.refreshTokenExpireTime()))
                .build();
    }

    private static AccountName accountName(AccountRecord account) {
        return AccountName.of(UserName.of(account.getUserId()), account.getId());
    }

    private static Timestamp timestamp(LocalDateTime utc) {
        return timestamp(utc.toInstant(ZoneOffset.UTC));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
