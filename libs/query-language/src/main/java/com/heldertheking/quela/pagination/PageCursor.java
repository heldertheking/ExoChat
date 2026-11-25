package com.heldertheking.quela.pagination;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.Result;
import org.jspecify.annotations.Nullable;

public class PageCursor {
    private final String pageToken;
    private final Condition cursorCondition;
    private final PaginationRequest request;
    private final Paginator paginator;
    private final boolean isLastPage;

    PageCursor(
            String pageToken,
            Condition cursorCondition,
            PaginationRequest request,
            Paginator paginator,
            boolean isLastPage) {
        this.pageToken = pageToken;
        this.cursorCondition = cursorCondition;
        this.request = request;
        this.paginator = paginator;
        this.isLastPage = isLastPage;
    }

    public Condition getCursorCondition() {
        return cursorCondition;
    }

    public String getPageToken() {
        return pageToken;
    }

    PaginationRequest getRequest() {
        return request;
    }

    public int getPageSize() {
        return request.pageSize();
    }

    /// true if the last page has been reached. It is technically possible that a cursor returns false and then
    /// the next page is empty. This is because the cursor is not aware of the total number of pages. However,
    /// there is a guarantee that when looping through pages isLastPage will eventually return true.
    ///
    /// @return true if the last page has been reached.
    public boolean isLastPage() {
        return isLastPage;
    }

    /// Returns the next page token for the given last record of the current page. This method is called after the
    /// current page has been fetched and the last record of the current page is known.
    ///
    /// @param lastRecordOfCurrentPage last record of current page. Might be null if the current page is empty.
    /// @return [PageCursor] for the next page or null if there is no next page.
    public PageCursor nextPage(@Nullable Record lastRecordOfCurrentPage) {
        var nextPageToken = paginator.nextPage(this, lastRecordOfCurrentPage);
        return new PageCursor(nextPageToken, cursorCondition, request, paginator, nextPageToken.isEmpty());
    }

    /// Returns the next page token for the given result. The result might be empty but never null.
    ///
    /// @param result result as returned from the query that fetched the current page.
    /// @return [PageCursor] for the next page.
    public PageCursor nextPage(Result<Record> result) {
        return nextPage(result.size() < request.pageSize() ? null : result.getLast());
    }
}
