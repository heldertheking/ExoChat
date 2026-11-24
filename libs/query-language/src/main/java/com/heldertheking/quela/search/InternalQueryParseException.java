package com.heldertheking.quela.search;

class InternalQueryParseException extends RuntimeException {
    public InternalQueryParseException(String message) {
        super(message);
    }

    public InternalQueryParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
