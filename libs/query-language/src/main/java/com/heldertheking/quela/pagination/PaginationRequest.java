package com.heldertheking.quela.pagination;

import java.util.stream.Stream;

import com.heldertheking.quela.orderby.OrderByCondition;

/// PaginationRequest contains all relevant parameters for a pagination request.
///
/// The values returned from this interface must not change when scrolling through pages. A
/// [PageCursor] will not be considered valid if these params change
public interface PaginationRequest {
    /// A propertyParser identifying the list endpoint. Used to destinguish page token for different list endpoints.
    /// A good choice is to set it to the resource name
    String pageType();

    /// string value of the pagination token. Usually, set to the page_token property of the list request
    String paginationToken();

    /// The page size of the list request
    int pageSize();

    /// [OrderByCondition] as returned from a [com.heldertheking.quela.orderby.OrderByParser] based on the order_by
    /// field in list requests
    OrderByCondition orderByCondition();

    /// Filter string used in list request
    String filter();

    /// additionalPaginationParams returns a stream of the pagination parameters that must not change
    /// when scrolling through pages. Type, order by and the filter are already considered by the
    /// [#pageType()], [#orderByCondition()] and [#filter()] methods. This method should return all other
    /// parameters that are part of the pagination request like
    ///
    ///     - parent
    ///
    /// @return a stream of the pagination parameters that must not change when scrolling through pages.
    Stream<String> additionalPaginationParams();
}
