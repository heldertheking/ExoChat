package com.heldertheking.quela;

import com.heldertheking.quela.orderby.OrderByCondition;
import com.heldertheking.quela.orderby.OrderByParser;
import com.heldertheking.quela.pagination.PageCursor;
import com.heldertheking.quela.pagination.PaginationRequest;
import com.heldertheking.quela.pagination.Paginator;
import com.heldertheking.quela.search.SearchParser;
import org.jooq.Condition;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/// AbstractFilter is a base class for all repository filters used in list queries.
/// It contains all necessary information to build a list query with pagination, filtering and sorting.
///
/// AbstractFilter is used to bridge between gRPC services and databases access. It hides the jooq db query layer
/// from the gRPC service layer
///
/// This class helps repositories to define its own Filter classes that can be used to build list queries.
///
/// ```
///
///      public class MyFilter extends AbstractFilter {
///          public MyFilter(Condition baseCondition, PaginationParams params) {
///              super(baseCondition, params);
///          }
///
///          public class MyFilterBuilder extends AbstractFilter.Builder<Builder, Filter>  {
///              public MyFilterBuilder(Paginator paginator, OrderByParser orderByParser, SearchParser searchParser) {
///                  super(paginator, orderByParser, searchParser);
///              }
///
///              public MyFilter someBusinessSpecificFilterMethod() {
///                  baseCondition = baseCondition.and(SOME_PROP.eq("abc"));
///                  additionalPaginationParams().add("some_param");
///              }
///
///              public MyFilter build() {
///                  return new MyFilter(baseCondition, buildPaginationParams());
///              }
///         }
///     }
///
/// ```
public abstract class AbstractFilter {
    protected final PaginationParams params;
    protected final Condition baseCondition;

    protected AbstractFilter(Condition baseCondition, PaginationParams params) {
        this.baseCondition = baseCondition;
        this.params = params;
    }

    public int getPageSize() {
        return params.pageCursor.getPageSize();
    }

    public List<SortField<?>> getSortFields() {
        return params.orderByClause().sortFields();
    }

    public Condition getMergedCondition() {
        return baseCondition.and(params.filterCondition()).and(getCursorCondition());
    }

    public Condition getTotalCondition() {
        return baseCondition.and(params.filterCondition());
    }

    public Condition getCursorCondition() {
        return params.pageCursor().getCursorCondition();
    }

    public PageCursor getPageToken() {
        return params.pageCursor();
    }

    protected record PaginationParams(
            Condition baseCondition,
            Condition filterCondition,
            OrderByCondition orderByClause,
            PageCursor pageCursor
    ) {
    }

    public abstract static class Builder<T extends Builder<T, F>, F extends AbstractFilter> {
        protected final Paginator paginator;
        protected final OrderByParser orderByParser;
        protected final SearchParser searchParser;
        protected Condition baseCondition;
        @Nullable protected Integer pageSize;
        @Nullable protected String filter;
        @Nullable protected String orderBy;
        @Nullable protected String pageToken;


        protected Builder(Paginator paginator, OrderByParser orderByParser, SearchParser searchParser) {
            this.paginator = paginator;
            this.orderByParser = orderByParser;
            this.searchParser = searchParser;
            baseCondition = DSL.noCondition();
        }

        protected Builder(Paginator paginator, OrderByParser orderByParser, SearchParser searchParser, Condition baseCondition) {
            this.paginator = paginator;
            this.orderByParser = orderByParser;
            this.searchParser = searchParser;
            this.baseCondition = baseCondition;
        }

        public T withFilter(String filter) {
            this.filter = filter;
            return self();
        }

        public T withPageSize(int pageSize) {
            this.pageSize = pageSize;
            return self();
        }

        public T withOrderBy(String orderBy) {
            this.orderBy = orderBy;
            return self();
        }

        public T withPageToken(String pageToken) {
            this.pageToken = pageToken;
            return self();
        }

        protected int getDefaultPageSize() {
            return 25;
        }

        protected int maxPageSize() {
            return 100;
        }

        @SuppressWarnings("unchecked")
        private T self() {
            return (T) this;
        }

        /// implementations should return additional pagination parameters that must not change when scrolling through
        /// pages. orderBy and the filter are already considered by the [#withOrderBy(String)] and [#withFilter(String)]
        /// methods. This method should return all other parameters that are part of the pagination request like
        ///
        ///     - parent
        protected abstract List<String> additionalPaginationParams();

        /// Builds the final filter object that can be passed to list queries.
        public abstract F build();

        /// Builds the pagination parameters for the filter object.
        /// This method is helps when building the final filter object in the [#build()] method.
        protected PaginationParams buildPaginationParams() {
            var pageSize = Math.max(this.pageSize == null || this.pageSize == 0 ? getDefaultPageSize() : Math.min(this.pageSize, maxPageSize()), 1);
            var paginationParams = new PageRequest(
                    this.getClass().getName(),
                    pageToken,
                    orderByParser.parse(orderBy),
                    filter == null ? "" : filter,
                    additionalPaginationParams(),
                    pageSize
            );
            var cursor = paginator.parse(paginationParams);
            var filterCondition = searchParser.parse(filter);
            return new PaginationParams(
                    baseCondition,
                    filterCondition,
                    paginationParams.orderByCondition(),
                    cursor
            );
        }

        private record PageRequest(String pageType, @Nullable String paginationToken, OrderByCondition orderByCondition,
                                   String filter, List<String> additionalParams,
                                   int pageSize) implements PaginationRequest {

            @Override
            public Stream<String> additionalPaginationParams() {
                return additionalParams.stream();
            }
        }
    }
}
