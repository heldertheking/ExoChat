package com.heldertheking.exochat.app.identity;

import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT;
import static com.heldertheking.exochat.db.schema.Tables.USER;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import com.heldertheking.exochat.api.id.UuidV7;
import com.heldertheking.exochat.app.permission.Standing;
import com.heldertheking.exochat.db.schema.tables.records.AccountRecord;
import com.heldertheking.quela.FieldResolverImpl;
import com.heldertheking.quela.QueryFieldParser;
import com.heldertheking.quela.orderby.AntlrOrderByParser;
import com.heldertheking.quela.orderby.OrderByParser;
import com.heldertheking.quela.pagination.Base64PaginationTokenSerializer;
import com.heldertheking.quela.pagination.ListPage;
import com.heldertheking.quela.pagination.Paginator;
import com.heldertheking.quela.pagination.PaginatorImpl;
import com.heldertheking.quela.search.AntlrSearchParser;
import com.heldertheking.quela.search.SearchParser;

/// Reads and writes accounts. The list supports `filter` and `order_by` on `username`, `display_name` and
/// `create_time` (quela, ADR-010).
@Repository
class AccountRepository {
    private final DSLContext dsl;
    private final Paginator paginator = new PaginatorImpl(new Base64PaginationTokenSerializer());
    private final OrderByParser orderByParser;
    private final SearchParser searchParser;

    AccountRepository(DSLContext dsl) {
        this.dsl = dsl;
        var fields = FieldResolverImpl.newBuilder()
                .addSortableField(QueryFieldParser.STRING, "username", ACCOUNT.USERNAME)
                .addSortableField(QueryFieldParser.STRING, "display_name", ACCOUNT.DISPLAY_NAME)
                .addSortableField(
                        QueryFieldParser.LOCAL_DATE_TIME.atZone(ZoneOffset.UTC), "create_time", ACCOUNT.CREATE_TIME)
                .build();
        this.orderByParser = AntlrOrderByParser.newBuilder()
                .withDiscriminator(QueryFieldParser.UUID, ACCOUNT.ID)
                .withFieldResolver(fields)
                .build();
        this.searchParser =
                AntlrSearchParser.newBuilder().withFieldResolver(fields).build();
    }

    /// One page of the accounts of a user.
    ///
    /// @throws RuntimeException the quela exceptions if filter, order or token are invalid
    ListPage<AccountRecord> list(UUID userId, String filter, String orderBy, int pageSize, String pageToken) {
        var builder = AccountFilter.newBuilder(paginator, orderByParser, searchParser, userId)
                .withPageSize(pageSize);
        if (!filter.isEmpty()) {
            builder.withFilter(filter);
        }
        if (!orderBy.isEmpty()) {
            builder.withOrderBy(orderBy);
        }
        if (!pageToken.isEmpty()) {
            builder.withPageToken(pageToken);
        }
        var query = builder.build();
        var rows = dsl.selectFrom(ACCOUNT)
                .where(query.getMergedCondition())
                .orderBy(query.getSortFields())
                .limit(query.getPageSize())
                .fetch();
        var lastOfFullPage = rows.size() < query.getPageSize() ? null : rows.getLast();
        var next = query.getPageToken().nextPage(lastOfFullPage);
        return new ListPage<>(rows, next.getPageToken());
    }

    /// The account, only if it belongs to the user named in its resource name.
    Optional<AccountRecord> find(UUID userId, UUID accountId) {
        return dsl.selectFrom(ACCOUNT)
                .where(ACCOUNT.ID.eq(accountId).and(ACCOUNT.USER_ID.eq(userId)))
                .fetchOptional();
    }

    /// Creates an account unless the user already has `maxAccounts`. The user row is locked so that concurrent
    /// creates and deletes of its accounts are serialized.
    ///
    /// @return the new account, empty if the limit is reached
    Optional<AccountRecord> create(
            UUID userId,
            String username,
            String displayName,
            @Nullable String avatarUrl,
            LocalDateTime now,
            int maxAccounts) {
        return dsl.transactionResult(configuration -> {
            var tx = configuration.dsl();
            if (lockAndCount(tx, userId) >= maxAccounts) {
                return Optional.empty();
            }
            var account = tx.newRecord(ACCOUNT);
            account.setId(UuidV7.next());
            account.setUserId(userId);
            account.setUsername(username);
            account.setDisplayName(displayName);
            account.setAvatarUrl(avatarUrl);
            account.setStanding(Standing.MEMBER.name());
            account.setCreateTime(now);
            account.insert();
            return Optional.of(account);
        });
    }

    /// Stores the changed fields of the record.
    void update(AccountRecord account) {
        account.attach(dsl.configuration());
        account.update();
    }

    /// Deletes an account unless it is the last one of its user, a user without accounts could do nothing. Sessions
    /// that had it active lose it (foreign key) and fall back to another account on their next refresh.
    DeleteResult delete(UUID userId, UUID accountId) {
        return dsl.transactionResult(configuration -> {
            var tx = configuration.dsl();
            var count = lockAndCount(tx, userId);
            if (tx.fetchCount(ACCOUNT, ACCOUNT.ID.eq(accountId).and(ACCOUNT.USER_ID.eq(userId))) == 0) {
                return DeleteResult.NOT_FOUND;
            }
            if (count <= 1) {
                return DeleteResult.LAST_ACCOUNT;
            }
            tx.deleteFrom(ACCOUNT)
                    .where(ACCOUNT.ID.eq(accountId).and(ACCOUNT.USER_ID.eq(userId)))
                    .execute();
            return DeleteResult.DELETED;
        });
    }

    private static int lockAndCount(DSLContext tx, UUID userId) {
        tx.select(USER.ID).from(USER).where(USER.ID.eq(userId)).forUpdate().fetch();
        return tx.fetchCount(ACCOUNT, ACCOUNT.USER_ID.eq(userId));
    }

    enum DeleteResult {
        DELETED,
        NOT_FOUND,
        LAST_ACCOUNT
    }
}
