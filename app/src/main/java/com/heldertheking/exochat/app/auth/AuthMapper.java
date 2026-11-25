package com.heldertheking.exochat.app.auth;

import java.time.Instant;
import java.time.LocalDateTime;

import com.google.protobuf.Timestamp;
import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.SessionName;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.app.identity.IdentityMapper;
import com.heldertheking.exochat.auth.v1alpha1.AuthTokens;
import com.heldertheking.exochat.db.schema.tables.records.SessionRecord;
import com.heldertheking.exochat.identity.v1alpha1.Session;

/// Converts database records to the protobuf messages. Database timestamps are UTC.
final class AuthMapper {
    private AuthMapper() {}

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

    static AuthTokens tokens(AuthRepository.Authenticated authenticated) {
        return AuthTokens.newBuilder()
                .setAccessToken(authenticated.accessToken())
                .setAccessTokenExpireTime(timestamp(authenticated.accessTokenExpireTime()))
                .setRefreshToken(authenticated.refreshToken())
                .setRefreshTokenExpireTime(timestamp(authenticated.refreshTokenExpireTime()))
                .build();
    }

    private static Timestamp timestamp(LocalDateTime utc) {
        return IdentityMapper.timestamp(utc);
    }

    static Timestamp timestamp(Instant instant) {
        return IdentityMapper.timestamp(instant);
    }
}
