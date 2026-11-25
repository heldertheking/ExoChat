package com.heldertheking.quela.pagination;

import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

public interface Paginator {
    /// Parses the pagination request and returns a page token.
    ///
    /// @param request
    /// @return
    PageCursor parse(PaginationRequest request);

    /// Returns the next page token. Implementations need to be aware of the last record of the current page might be
    /// null. If the page did not reach the expected page size.
    ///
    /// @param token
    /// @param lastRecord
    /// @return
    String nextPage(PageCursor token, @Nullable Record lastRecord);

    /// creates a new page token for the first page.
    ///
    /// @param request the request for the first page. The returned token is only valid for requests
    ///                with the same filter, order by and additional pagination params.
    /// @return
    default PageCursor firstPage(PaginationRequest request) {
        return new PageCursor("", DSL.noCondition(), request, this, false);
    }
}
