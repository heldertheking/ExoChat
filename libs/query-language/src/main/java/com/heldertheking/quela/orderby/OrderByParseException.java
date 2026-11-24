package com.heldertheking.quela.orderby;

/// Exception that is thrown if an error occurs while parsing an order by string.
public class OrderByParseException extends RuntimeException {
    private final String orderByClause;

    OrderByParseException(String orderByClause, String message, Throwable cause) {
        super(message, cause);
        this.orderByClause = orderByClause;
    }

    public String getOrderByClause() {
        return orderByClause;
    }
}
