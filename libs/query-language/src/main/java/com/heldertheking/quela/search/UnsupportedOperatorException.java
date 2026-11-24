package com.heldertheking.quela.search;

public class UnsupportedOperatorException extends RuntimeException {
    private final String operator;

    public UnsupportedOperatorException(String operator) {
        super("Unsupported operator: " + operator);
        this.operator = operator;
    }

    public String getOperator() {
        return operator;
    }
}
