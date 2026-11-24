package com.heldertheking.quela;

public class FieldResolutionException extends RuntimeException {
    private final String field;

    public FieldResolutionException(String field) {
        super("Unknown field `%s` found in query".formatted(field));
        this.field = field;
    }

    public FieldResolutionException(String message, String field) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
