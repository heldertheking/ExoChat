package com.heldertheking.exochat.app.identity;

import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT;
import static com.heldertheking.exochat.db.schema.Tables.USER;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.google.protobuf.FieldMask;
import com.heldertheking.exochat.api.id.UuidV7;
import com.heldertheking.exochat.app.grpc.MaskedMapper;
import com.heldertheking.exochat.app.permission.Standing;
import com.heldertheking.exochat.db.schema.tables.records.AccountRecord;
import com.heldertheking.exochat.identity.v1alpha1.Account;
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

import io.grpc.Status;

/// The service layer of the accounts: reads and writes them with jOOQ. The list supports `filter` and `order_by`
/// on `username`, `display_name` and `create_time` (quela, ADR-010).
@Repository
class AccountRepository {
    /// A user can own this many accounts.
    static final int MAX_ACCOUNTS_PER_USER = 5;

    private final DSLContext dsl;
    private final Clock clock;
    private final Paginator paginator = new PaginatorImpl(new Base64PaginationTokenSerializer());
    private final OrderByParser orderByParser;
    private final SearchParser searchParser;

    AccountRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
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

    /// Creates an account for the user. The user row is locked so that concurrent creates and deletes of its
    /// accounts are serialized.
    ///
    /// @throws io.grpc.StatusRuntimeException INVALID_ARGUMENT for an invalid value, ALREADY_EXISTS if the username
    ///         is taken, FAILED_PRECONDITION if the user already has [#MAX_ACCOUNTS_PER_USER] accounts
    AccountRecord create(UUID userId, Account requested) {
        var username = IdentityInput.requireUsername(requested.getUsername());
        var display = IdentityInput.optionalDisplayName(requested.getDisplayName());
        var avatar = IdentityInput.optionalAvatarUrl(requested.getAvatarUrl());
        var now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        try {
            return dsl.transactionResult(configuration -> {
                var tx = configuration.dsl();
                if (lockAndCount(tx, userId) >= MAX_ACCOUNTS_PER_USER) {
                    throw Status.FAILED_PRECONDITION
                            .withDescription("a user can have at most %d accounts".formatted(MAX_ACCOUNTS_PER_USER))
                            .asRuntimeException();
                }
                var account = tx.newRecord(ACCOUNT);
                account.setId(UuidV7.next());
                account.setUserId(userId);
                account.setUsername(username);
                account.setDisplayName(display == null ? username : display);
                account.setAvatarUrl(avatar);
                account.setStanding(Standing.MEMBER.name());
                account.setCreateTime(now);
                account.insert();
                return account;
            });
        } catch (DataIntegrityViolationException e) {
            throw IdentityInput.duplicate(e);
        }
    }

    /// Updates `display_name` and `avatar_url`, the username never changes. With a mask exactly the named
    /// properties change (an empty `avatar_url` removes the avatar), without one the properties that are set in
    /// `changes` do.
    ///
    /// @throws io.grpc.StatusRuntimeException NOT_FOUND, INVALID_ARGUMENT for an invalid value
    /// @throws com.heldertheking.exochat.app.grpc.UnmappedPropertyException if the mask names another property
    AccountRecord update(UUID userId, UUID accountId, Account changes, @Nullable FieldMask mask) {
        var account = find(userId, accountId)
                .orElseThrow(() ->
                        Status.NOT_FOUND.withDescription("account not found").asRuntimeException());
        boolean explicit = mask != null;
        MaskedMapper.forMask(mask)
                .setIfMaskedAndPresent(
                        "display_name",
                        explicit || !changes.getDisplayName().isEmpty(),
                        () -> {
                            var display = IdentityInput.optionalDisplayName(changes.getDisplayName());
                            if (display == null) {
                                throw IdentityInput.invalid("display_name must not be empty");
                            }
                            return display;
                        },
                        account::setDisplayName)
                .setIfMaskedAndPresent(
                        "avatar_url",
                        explicit || !changes.getAvatarUrl().isEmpty(),
                        () -> IdentityInput.optionalAvatarUrl(changes.getAvatarUrl()),
                        account::setAvatarUrl)
                .throwOnUnmappedProperty();
        if (account.changed()) {
            account.attach(dsl.configuration());
            account.update();
        }
        return account;
    }

    /// Deletes an account unless it is the last one of its user, a user without accounts could do nothing.
    /// Sessions that had it active lose it (foreign key) and fall back to another account on their next refresh.
    ///
    /// @throws io.grpc.StatusRuntimeException NOT_FOUND, FAILED_PRECONDITION for the last account
    void delete(UUID userId, UUID accountId) {
        dsl.transaction(configuration -> {
            var tx = configuration.dsl();
            var count = lockAndCount(tx, userId);
            if (tx.fetchCount(ACCOUNT, ACCOUNT.ID.eq(accountId).and(ACCOUNT.USER_ID.eq(userId))) == 0) {
                throw Status.NOT_FOUND.withDescription("account not found").asRuntimeException();
            }
            if (count <= 1) {
                throw Status.FAILED_PRECONDITION
                        .withDescription("the last account of a user can not be deleted")
                        .asRuntimeException();
            }
            tx.deleteFrom(ACCOUNT)
                    .where(ACCOUNT.ID.eq(accountId).and(ACCOUNT.USER_ID.eq(userId)))
                    .execute();
        });
    }

    private static int lockAndCount(DSLContext tx, UUID userId) {
        tx.select(USER.ID).from(USER).where(USER.ID.eq(userId)).forUpdate().fetch();
        return tx.fetchCount(ACCOUNT, ACCOUNT.USER_ID.eq(userId));
    }
}
