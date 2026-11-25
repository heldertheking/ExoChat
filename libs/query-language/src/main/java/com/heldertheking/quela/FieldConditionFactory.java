package com.heldertheking.quela;

import java.util.Collection;

import org.jooq.Condition;
import org.jooq.Field;

public class FieldConditionFactory<T> implements ConditionFactory<T> {
    protected final Field<T> field;

    public FieldConditionFactory(Field<T> field) {
        this.field = field;
    }

    @Override
    public Condition eq(T value) {
        return field.eq(value);
    }

    @Override
    public Condition ne(T value) {
        return field.ne(value);
    }

    @Override
    public Condition gt(T value) {
        return field.gt(value);
    }

    @Override
    public Condition ge(T value) {
        return field.ge(value);
    }

    @Override
    public Condition lt(T value) {
        return field.lt(value);
    }

    @Override
    public Condition le(T value) {
        return field.le(value);
    }

    @Override
    public Condition isNull() {
        return field.isNull();
    }

    @Override
    public Condition isNotNull() {
        return field.isNotNull();
    }

    @Override
    public Condition in(Collection<T> value) {
        return field.in(value);
    }
}
