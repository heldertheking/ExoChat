package com.heldertheking.exochat.app.identity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.google.protobuf.Timestamp;
import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.db.schema.tables.records.AccountRecord;
import com.heldertheking.exochat.db.schema.tables.records.UserRecord;
import com.heldertheking.exochat.identity.v1alpha1.Account;
import com.heldertheking.exochat.identity.v1alpha1.User;

/// Converts users and accounts from database records to protobuf messages. Database timestamps are UTC.
public final class IdentityMapper {
    private IdentityMapper() {}

    public static User user(UserRecord user) {
        var builder = User.newBuilder()
                .setName(UserName.of(user.getId()).toString())
                .setEmail(user.getEmail())
                .setCreateTime(timestamp(user.getCreateTime()));
        if (user.getPhoneNumber() != null) {
            builder.setPhoneNumber(user.getPhoneNumber());
        }
        return builder.build();
    }

    /// @param includeStanding whether to tell the standing, only the owner learns it
    public static Account account(AccountRecord account, boolean includeStanding) {
        var builder = Account.newBuilder()
                .setName(AccountName.of(UserName.of(account.getUserId()), account.getId())
                        .toString())
                .setUsername(account.getUsername())
                .setDisplayName(account.getDisplayName())
                .setCreateTime(timestamp(account.getCreateTime()));
        if (account.getAvatarUrl() != null) {
            builder.setAvatarUrl(account.getAvatarUrl());
        }
        if (includeStanding) {
            builder.setStanding(standing(account.getStanding()));
        }
        return builder.build();
    }

    private static Account.AccountStanding standing(String standing) {
        try {
            return Account.AccountStanding.valueOf("ACCOUNT_STANDING_" + standing);
        } catch (IllegalArgumentException e) {
            return Account.AccountStanding.ACCOUNT_STANDING_UNSPECIFIED;
        }
    }

    public static Timestamp timestamp(LocalDateTime utc) {
        return timestamp(utc.toInstant(ZoneOffset.UTC));
    }

    public static Timestamp timestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
