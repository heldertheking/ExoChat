package com.heldertheking.quela;

import java.util.Collection;

import org.jooq.Condition;

/// @param <V> field / row propertyParser
public interface ConditionFactory<V> {

    Condition eq(V value);

    Condition ne(V value);

    Condition gt(V value);

    Condition ge(V value);

    Condition lt(V value);

    Condition le(V value);

    Condition isNull();

    Condition isNotNull();

    Condition in(Collection<V> value);
}
