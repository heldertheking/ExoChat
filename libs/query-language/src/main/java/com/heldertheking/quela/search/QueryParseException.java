package com.heldertheking.quela.search;

public class QueryParseException extends RuntimeException {
    private final String query;

    public QueryParseException(String message, Throwable cause, String query) {
        super(message, cause);
        this.query = query;
    }

    public QueryParseException(String message, String query) {
        super(message);
        this.query = query;
    }

    public String getQuery() {
        return query;
    }
}
