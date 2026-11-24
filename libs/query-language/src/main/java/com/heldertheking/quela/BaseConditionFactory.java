package com.heldertheking.quela;

import com.heldertheking.quela.search.UnsupportedOperatorException;
import org.jooq.Condition;

import java.util.Collection;

public abstract class BaseConditionFactory<T> implements ConditionFactory<T> {
    @Override
    public Condition eq(T value) {
        throw new UnsupportedOperatorException("=");
    }

    @Override
    public Condition ne(T value) {
        throw new UnsupportedOperatorException("!=");
    }

    @Override
    public Condition gt(T value) {
        throw new UnsupportedOperatorException(">");
    }

    @Override
    public Condition ge(T value) {
        throw new UnsupportedOperatorException(">=");
    }

    @Override
    public Condition lt(T value) {
        throw new UnsupportedOperatorException("<");
    }

    @Override
    public Condition le(T value) {
        throw new UnsupportedOperatorException("<=");
    }

    @Override
    public Condition isNull() {
        throw new UnsupportedOperatorException("= null");
    }

    @Override
    public Condition isNotNull() {
        throw new UnsupportedOperatorException("!= null");
    }

    @Override
    public Condition in(Collection<T> value) {
        throw new UnsupportedOperatorException("in");
    }
}
