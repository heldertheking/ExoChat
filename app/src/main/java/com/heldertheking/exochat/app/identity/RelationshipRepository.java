package com.heldertheking.exochat.app.identity;

import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT;
import static com.heldertheking.exochat.db.schema.Tables.RELATIONSHIP;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.heldertheking.exochat.api.id.UuidV7;
import com.heldertheking.exochat.db.schema.tables.records.AccountRecord;
import com.heldertheking.exochat.db.schema.tables.records.RelationshipRecord;
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
import io.grpc.StatusRuntimeException;

/// The service layer of the social links between accounts: reads them (with the user of the target account,
/// needed for its resource name) and runs the state machine of requests, friendships and blocks. The list supports
/// `filter` and `order_by` on `type` and `create_time` (quela, ADR-010).
///
/// Every friendship or pending request is stored once on each side (the sender has PENDING_OUTGOING, the receiver
/// PENDING_INCOMING, both FRIEND after the accept), a block only on the side of the blocker so that the blocked
/// account learns nothing about it. The two rows of a pair are always locked and changed together in one
/// transaction. Problems are [io.grpc.StatusRuntimeException]s with the status the caller should see.
@Repository
class RelationshipRepository {
    private final DSLContext dsl;
    private final Clock clock;
    private final Paginator paginator = new PaginatorImpl(new Base64PaginationTokenSerializer());
    private final OrderByParser orderByParser;
    private final SearchParser searchParser;

    RelationshipRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
        var fields = FieldResolverImpl.newBuilder()
                .addSortableField(QueryFieldParser.STRING, "type", RELATIONSHIP.TYPE)
                .addSortableField(
                        QueryFieldParser.LOCAL_DATE_TIME.atZone(ZoneOffset.UTC),
                        "create_time",
                        RELATIONSHIP.CREATE_TIME)
                .build();
        this.orderByParser = AntlrOrderByParser.newBuilder()
                .withDiscriminator(QueryFieldParser.UUID, RELATIONSHIP.ID)
                .withFieldResolver(fields)
                .build();
        this.searchParser =
                AntlrSearchParser.newBuilder().withFieldResolver(fields).build();
    }

    /// A relationship and the user that owns its target account.
    record RelationshipView(RelationshipRecord relationship, UUID targetUserId) {}

    /// One page of the relationships of an account.
    ///
    /// @throws RuntimeException the quela exceptions if filter, order or token are invalid
    ListPage<RelationshipView> list(
            UUID ownerAccountId, String filter, String orderBy, int pageSize, String pageToken) {
        var builder = RelationshipFilter.newBuilder(paginator, orderByParser, searchParser, ownerAccountId)
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
        var rows = dsl.select(RELATIONSHIP.fields())
                .select(ACCOUNT.USER_ID)
                .from(RELATIONSHIP)
                .join(ACCOUNT)
                .on(ACCOUNT.ID.eq(RELATIONSHIP.TARGET_ACCOUNT_ID))
                .where(query.getMergedCondition())
                .orderBy(query.getSortFields())
                .limit(query.getPageSize())
                .fetch();
        var lastOfFullPage = rows.size() < query.getPageSize() ? null : rows.getLast();
        var next = query.getPageToken().nextPage(lastOfFullPage);
        var views = new ArrayList<RelationshipView>(rows.size());
        for (var row : rows) {
            views.add(new RelationshipView(row.into(RELATIONSHIP), row.get(ACCOUNT.USER_ID)));
        }
        return new ListPage<>(views, next.getPageToken());
    }

    /// The relationship, only if the account owns it.
    Optional<RelationshipView> find(UUID ownerAccountId, UUID relationshipId) {
        return dsl.select(RELATIONSHIP.fields())
                .select(ACCOUNT.USER_ID)
                .from(RELATIONSHIP)
                .join(ACCOUNT)
                .on(ACCOUNT.ID.eq(RELATIONSHIP.TARGET_ACCOUNT_ID))
                .where(RELATIONSHIP.ID.eq(relationshipId).and(RELATIONSHIP.OWNER_ACCOUNT_ID.eq(ownerAccountId)))
                .fetchOptional()
                .map(row -> new RelationshipView(row.into(RELATIONSHIP), row.get(ACCOUNT.USER_ID)));
    }

    static final String FRIEND = "FRIEND";
    static final String PENDING_INCOMING = "PENDING_INCOMING";
    static final String PENDING_OUTGOING = "PENDING_OUTGOING";
    static final String BLOCKED = "BLOCKED";

    /// Sends a friend request from the owner to the target. If the target already asked the owner, both
    /// become friends. If the target blocked the owner, the request is stored on the side of the owner only
    /// and never reaches the target, the owner cannot tell.
    ///
    /// @throws StatusRuntimeException NOT_FOUND if the target does not exist, INVALID_ARGUMENT for an account
    ///         of the same user, ALREADY_EXISTS for an existing request or friendship, FAILED_PRECONDITION
    ///         if the owner blocked the target
    RelationshipView sendRequest(UUID ownerId, UUID targetId) {
        try {
            return dsl.transactionResult(configuration -> {
                var tx = configuration.dsl();
                var target = requireOtherUsersAccount(tx, ownerId, targetId);
                var pair = lock(tx, ownerId, targetId);
                var now = now();
                var existing = pair.mine();
                if (existing != null) {
                    var type = existing.getType();
                    if (FRIEND.equals(type) || PENDING_OUTGOING.equals(type)) {
                        throw status(Status.ALREADY_EXISTS, "there already is a friendship or a request");
                    }
                    if (BLOCKED.equals(type)) {
                        throw status(Status.FAILED_PRECONDITION, "unblock the account before sending a request");
                    }
                    // PENDING_INCOMING: the target asked first, asking back is accepting
                    return befriend(tx, pair, target.getUserId());
                }
                var mine = insert(tx, ownerId, targetId, PENDING_OUTGOING, now);
                var theirs = pair.theirs();
                if (theirs == null) {
                    insert(tx, targetId, ownerId, PENDING_INCOMING, now);
                } else if (!BLOCKED.equals(theirs.getType())) {
                    theirs.setType(PENDING_INCOMING);
                    theirs.update();
                }
                return new RelationshipView(mine, target.getUserId());
            });
        } catch (DataIntegrityViolationException e) {
            // two requests at the same time: the unique pair constraint lets only one win
            throw status(Status.ALREADY_EXISTS, "there already is a friendship or a request");
        }
    }

    /// Accepts an incoming request of the owner.
    ///
    /// @throws StatusRuntimeException NOT_FOUND, FAILED_PRECONDITION if the relationship is not an incoming request
    RelationshipView accept(UUID ownerId, UUID relationshipId) {
        return dsl.transactionResult(configuration -> {
            var tx = configuration.dsl();
            var pair = lockById(tx, ownerId, relationshipId);
            if (!PENDING_INCOMING.equals(pair.mine().getType())) {
                throw status(Status.FAILED_PRECONDITION, "there is no incoming request to accept");
            }
            return befriend(tx, pair.pair(), targetUserId(tx, pair.mine().getTargetAccountId()));
        });
    }

    /// Rejects an incoming request of the owner, it disappears on both sides.
    ///
    /// @throws StatusRuntimeException NOT_FOUND, FAILED_PRECONDITION if the relationship is not an incoming request
    void reject(UUID ownerId, UUID relationshipId) {
        dsl.transaction(configuration -> {
            var pair = lockById(configuration.dsl(), ownerId, relationshipId);
            if (!PENDING_INCOMING.equals(pair.mine().getType())) {
                throw status(Status.FAILED_PRECONDITION, "there is no incoming request to reject");
            }
            removeBoth(pair.pair());
        });
    }

    /// Deletes a relationship of the owner: unfriends, withdraws a request, declines an incoming one or removes a
    /// block. The row of the other side goes with it, unless it is a block the other side has put on the owner.
    ///
    /// @throws StatusRuntimeException NOT_FOUND
    void delete(UUID ownerId, UUID relationshipId) {
        dsl.transaction(configuration -> {
            var pair = lockById(configuration.dsl(), ownerId, relationshipId);
            removeBoth(pair.pair());
        });
    }

    /// Blocks the target for the owner. Any friendship or request between them ends and disappears on the target's
    /// side, a block the target put on the owner stays.
    ///
    /// @throws StatusRuntimeException NOT_FOUND if the target does not exist, INVALID_ARGUMENT for an account of
    ///         the same user
    RelationshipView block(UUID ownerId, UUID targetId) {
        try {
            return dsl.transactionResult(configuration -> {
                var tx = configuration.dsl();
                var target = requireOtherUsersAccount(tx, ownerId, targetId);
                var pair = lock(tx, ownerId, targetId);
                var existing = pair.mine();
                RelationshipRecord mine;
                if (existing == null) {
                    mine = insert(tx, ownerId, targetId, BLOCKED, now());
                } else {
                    mine = existing;
                    if (!BLOCKED.equals(mine.getType())) {
                        mine.setType(BLOCKED);
                        mine.update();
                    }
                }
                var theirs = pair.theirs();
                if (theirs != null && !BLOCKED.equals(theirs.getType())) {
                    theirs.delete();
                }
                return new RelationshipView(mine, target.getUserId());
            });
        } catch (DataIntegrityViolationException e) {
            throw status(Status.ABORTED, "the relationship changed at the same time, try again");
        }
    }

    /// Makes both sides FRIEND. The row of the other side is created if it is missing.
    private RelationshipView befriend(DSLContext tx, Pair pair, UUID targetUserId) {
        var mine = pair.mine();
        if (mine == null) {
            throw new IllegalStateException("no relationship to befriend");
        }
        mine.setType(FRIEND);
        mine.update();
        var theirs = pair.theirs();
        if (theirs == null) {
            insert(tx, mine.getTargetAccountId(), mine.getOwnerAccountId(), FRIEND, now());
        } else if (!BLOCKED.equals(theirs.getType())) {
            theirs.setType(FRIEND);
            theirs.update();
        }
        return new RelationshipView(mine, targetUserId);
    }

    /// Deletes the row of the owner and the row of the other side, a block of the other side stays.
    private static void removeBoth(Pair pair) {
        var mine = pair.mine();
        if (mine != null) {
            mine.delete();
        }
        var theirs = pair.theirs();
        if (theirs != null && !BLOCKED.equals(theirs.getType())) {
            theirs.delete();
        }
    }

    /// The target must exist and belong to another user than the owner.
    private static AccountRecord requireOtherUsersAccount(DSLContext tx, UUID ownerId, UUID targetId) {
        var accounts =
                tx.selectFrom(ACCOUNT).where(ACCOUNT.ID.in(ownerId, targetId)).fetch();
        @Nullable AccountRecord owner = null;
        @Nullable AccountRecord target = null;
        for (var account : accounts) {
            if (account.getId().equals(ownerId)) {
                owner = account;
            }
            if (account.getId().equals(targetId)) {
                target = account;
            }
        }
        if (target == null) {
            throw status(Status.NOT_FOUND, "account not found");
        }
        if (owner == null) {
            throw status(Status.NOT_FOUND, "account not found");
        }
        if (owner.getUserId().equals(target.getUserId())) {
            throw status(
                    Status.INVALID_ARGUMENT, "an account can not have a relationship with an account of the same user");
        }
        return target;
    }

    private static UUID targetUserId(DSLContext tx, UUID accountId) {
        return Objects.requireNonNull(tx.select(ACCOUNT.USER_ID)
                .from(ACCOUNT)
                .where(ACCOUNT.ID.eq(accountId))
                .fetchSingle(ACCOUNT.USER_ID));
    }

    private record Pair(
            @Nullable RelationshipRecord mine, @Nullable RelationshipRecord theirs) {}

    private record LockedById(RelationshipRecord mine, Pair pair) {}

    /// Locks the rows of both sides, always in the same order, so that two requests at the same time cannot deadlock.
    private static Pair lock(DSLContext tx, UUID ownerId, UUID targetId) {
        var rows = tx.selectFrom(RELATIONSHIP)
                .where(RELATIONSHIP
                        .OWNER_ACCOUNT_ID
                        .eq(ownerId)
                        .and(RELATIONSHIP.TARGET_ACCOUNT_ID.eq(targetId))
                        .or(RELATIONSHIP.OWNER_ACCOUNT_ID.eq(targetId).and(RELATIONSHIP.TARGET_ACCOUNT_ID.eq(ownerId))))
                .orderBy(RELATIONSHIP.ID)
                .forUpdate()
                .fetch();
        @Nullable RelationshipRecord mine = null;
        @Nullable RelationshipRecord theirs = null;
        for (var row : rows) {
            if (row.getOwnerAccountId().equals(ownerId)) {
                mine = row;
            } else {
                theirs = row;
            }
        }
        return new Pair(mine, theirs);
    }

    /// Finds a relationship of the owner by id and locks it together with the row of the other side.
    private static LockedById lockById(DSLContext tx, UUID ownerId, UUID relationshipId) {
        var found = tx.selectFrom(RELATIONSHIP)
                .where(RELATIONSHIP.ID.eq(relationshipId).and(RELATIONSHIP.OWNER_ACCOUNT_ID.eq(ownerId)))
                .fetchOne();
        if (found == null) {
            throw status(Status.NOT_FOUND, "relationship not found");
        }
        var pair = lock(tx, ownerId, found.getTargetAccountId());
        var mine = pair.mine();
        if (mine == null || !mine.getId().equals(relationshipId)) {
            throw status(Status.NOT_FOUND, "relationship not found");
        }
        return new LockedById(mine, pair);
    }

    private static RelationshipRecord insert(
            DSLContext tx, UUID ownerId, UUID targetId, String type, LocalDateTime now) {
        var record = tx.newRecord(RELATIONSHIP);
        record.setId(UuidV7.next());
        record.setOwnerAccountId(ownerId);
        record.setTargetAccountId(targetId);
        record.setType(type);
        record.setCreateTime(now);
        record.insert();
        return record;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static StatusRuntimeException status(Status status, String description) {
        return status.withDescription(description).asRuntimeException();
    }
}
