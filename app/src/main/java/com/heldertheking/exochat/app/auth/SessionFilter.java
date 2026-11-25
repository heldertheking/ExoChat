package com.heldertheking.exochat.app.auth;

import static com.heldertheking.exochat.db.schema.Tables.SESSION;

import java.util.List;
import java.util.UUID;

import org.jooq.Condition;

import com.heldertheking.quela.AbstractFilter;
import com.heldertheking.quela.orderby.OrderByParser;
import com.heldertheking.quela.pagination.Paginator;
import com.heldertheking.quela.search.SearchParser;

/// List query over the sessions of one user: that user as base condition, filter, order and page token from the
/// request. Without `order_by` sessions come in creation order (the id is a UUIDv7).
final class SessionFilter extends AbstractFilter {

    private SessionFilter(Condition baseCondition, PaginationParams params) {
        super(baseCondition, params);
    }

    static Builder newBuilder(
            Paginator paginator, OrderByParser orderByParser, SearchParser searchParser, UUID userId) {
        return new Builder(paginator, orderByParser, searchParser, userId);
    }

    static final class Builder extends AbstractFilter.Builder<Builder, SessionFilter> {
        private final UUID userId;

        private Builder(Paginator paginator, OrderByParser orderByParser, SearchParser searchParser, UUID userId) {
            super(paginator, orderByParser, searchParser, SESSION.USER_ID.eq(userId));
            this.userId = userId;
        }

        @Override
        protected List<String> additionalPaginationParams() {
            return List.of(userId.toString());
        }

        @Override
        public SessionFilter build() {
            return new SessionFilter(baseCondition, buildPaginationParams());
        }
    }
}
