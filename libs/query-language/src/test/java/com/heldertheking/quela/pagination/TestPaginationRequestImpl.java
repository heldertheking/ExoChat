package com.heldertheking.quela.pagination;

import java.util.List;
import java.util.stream.Stream;

import com.heldertheking.quela.orderby.OrderByCondition;

record TestPaginationRequestImpl(
        String pageType,
        String paginationToken,
        OrderByCondition orderByCondition,
        String filter,
        List<String> additonalParams,
        int pageSize)
        implements PaginationRequest {
    public TestPaginationRequestImpl(
            String pageType, String paginationToken, OrderByCondition orderByCondition, String filter) {
        this(pageType, paginationToken, orderByCondition, filter, List.of());
    }

    public TestPaginationRequestImpl(
            String pageType,
            String paginationToken,
            OrderByCondition orderByCondition,
            String filter,
            List<String> additonalParams) {
        this(pageType, paginationToken, orderByCondition, filter, additonalParams, 25);
    }

    @Override
    public Stream<String> additionalPaginationParams() {
        return additonalParams.stream();
    }
}
