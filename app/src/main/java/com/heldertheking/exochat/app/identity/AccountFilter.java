package com.heldertheking.exochat.app.identity;

import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT;

import java.util.List;
import java.util.UUID;

import org.jooq.Condition;

import com.heldertheking.quela.AbstractFilter;
import com.heldertheking.quela.orderby.OrderByParser;
import com.heldertheking.quela.pagination.Paginator;
import com.heldertheking.quela.search.SearchParser;

/// List query over the accounts of one user. Without `order_by` accounts come in creation order (the id is a UUIDv7).
final class AccountFilter extends AbstractFilter {

    private AccountFilter(Condition baseCondition, PaginationParams params) {
        super(baseCondition, params);
    }

    static Builder newBuilder(
            Paginator paginator, OrderByParser orderByParser, SearchParser searchParser, UUID userId) {
        return new Builder(paginator, orderByParser, searchParser, userId);
    }

    static final class Builder extends AbstractFilter.Builder<Builder, AccountFilter> {
        private final UUID userId;

        private Builder(Paginator paginator, OrderByParser orderByParser, SearchParser searchParser, UUID userId) {
            super(paginator, orderByParser, searchParser, ACCOUNT.USER_ID.eq(userId));
            this.userId = userId;
        }

        @Override
        protected List<String> additionalPaginationParams() {
            return List.of(userId.toString());
        }

        @Override
        public AccountFilter build() {
            return new AccountFilter(baseCondition, buildPaginationParams());
        }
    }
}
