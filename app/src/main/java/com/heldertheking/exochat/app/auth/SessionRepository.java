package com.heldertheking.exochat.app.auth;

import static com.heldertheking.exochat.db.schema.Tables.SESSION;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import com.heldertheking.exochat.db.schema.tables.records.SessionRecord;
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

/// Reads and revokes sessions. The list supports `filter` and `order_by` on `device_label`, `create_time`,
/// `last_used_time` and `expire_time` (quela, ADR-010).
@Repository
class SessionRepository {
    private final DSLContext dsl;
    private final Paginator paginator = new PaginatorImpl(new Base64PaginationTokenSerializer());
    private final OrderByParser orderByParser;
    private final SearchParser searchParser;

    SessionRepository(DSLContext dsl) {
        this.dsl = dsl;
        var utc = QueryFieldParser.LOCAL_DATE_TIME.atZone(ZoneOffset.UTC);
        var fields = FieldResolverImpl.newBuilder()
                .addSortableField(QueryFieldParser.STRING, "device_label", SESSION.DEVICE_LABEL)
                .addSortableField(utc, "create_time", SESSION.CREATE_TIME)
                .addSortableField(utc, "last_used_time", SESSION.LAST_USED_TIME)
                .addSortableField(utc, "expire_time", SESSION.EXPIRE_TIME)
                .build();
        this.orderByParser = AntlrOrderByParser.newBuilder()
                .withDiscriminator(QueryFieldParser.UUID, SESSION.ID)
                .withFieldResolver(fields)
                .build();
        this.searchParser =
                AntlrSearchParser.newBuilder().withFieldResolver(fields).build();
    }

    /// One page of the sessions of a user.
    ///
    /// @param filter    AIP-160 filter, empty for none
    /// @param orderBy   order, empty for creation order
    /// @param pageSize  0 for the default
    /// @param pageToken token of the previous page, empty for the first
    /// @throws RuntimeException the quela exceptions if filter, order or token are invalid
    ListPage<SessionRecord> list(UUID userId, String filter, String orderBy, int pageSize, String pageToken) {
        var builder = SessionFilter.newBuilder(paginator, orderByParser, searchParser, userId)
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
        var rows = dsl.selectFrom(SESSION)
                .where(query.getMergedCondition())
                .orderBy(query.getSortFields())
                .limit(query.getPageSize())
                .fetch();
        var lastOfFullPage = rows.size() < query.getPageSize() ? null : rows.getLast();
        var next = query.getPageToken().nextPage(lastOfFullPage);
        return new ListPage<>(rows, next.getPageToken());
    }

    Optional<SessionRecord> find(UUID userId, UUID sessionId) {
        return dsl.selectFrom(SESSION)
                .where(SESSION.ID.eq(sessionId).and(SESSION.USER_ID.eq(userId)))
                .fetchOptional();
    }

    /// Deletes the session, which revokes its refresh token. True if it existed.
    boolean delete(UUID userId, UUID sessionId) {
        return dsl.deleteFrom(SESSION)
                        .where(SESSION.ID.eq(sessionId).and(SESSION.USER_ID.eq(userId)))
                        .execute()
                > 0;
    }
}
