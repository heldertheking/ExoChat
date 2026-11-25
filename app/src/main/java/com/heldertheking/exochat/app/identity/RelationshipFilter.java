package com.heldertheking.exochat.app.identity;

import static com.heldertheking.exochat.db.schema.Tables.RELATIONSHIP;

import java.util.List;
import java.util.UUID;

import org.jooq.Condition;

import com.heldertheking.quela.AbstractFilter;
import com.heldertheking.quela.orderby.OrderByParser;
import com.heldertheking.quela.pagination.Paginator;
import com.heldertheking.quela.search.SearchParser;

/// List query over the relationships of one account. Without `order_by` they come in creation order (the id is a
/// UUIDv7).
final class RelationshipFilter extends AbstractFilter {

    private RelationshipFilter(Condition baseCondition, PaginationParams params) {
        super(baseCondition, params);
    }

    static Builder newBuilder(
            Paginator paginator, OrderByParser orderByParser, SearchParser searchParser, UUID ownerAccountId) {
        return new Builder(paginator, orderByParser, searchParser, ownerAccountId);
    }

    static final class Builder extends AbstractFilter.Builder<Builder, RelationshipFilter> {
        private final UUID ownerAccountId;

        private Builder(
                Paginator paginator, OrderByParser orderByParser, SearchParser searchParser, UUID ownerAccountId) {
            super(paginator, orderByParser, searchParser, RELATIONSHIP.OWNER_ACCOUNT_ID.eq(ownerAccountId));
            this.ownerAccountId = ownerAccountId;
        }

        @Override
        protected List<String> additionalPaginationParams() {
            return List.of(ownerAccountId.toString());
        }

        @Override
        public RelationshipFilter build() {
            return new RelationshipFilter(baseCondition, buildPaginationParams());
        }
    }
}
