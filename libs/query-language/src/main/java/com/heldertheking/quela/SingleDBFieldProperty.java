package com.heldertheking.quela;

import org.jooq.Field;

public interface SingleDBFieldProperty<T, Parser extends QueryFieldParser<T>> {
    Parser getQueryPropertyParser();

    default ConditionFactory<T> conditionFactoryForField(Field<T> field) {
        return new FieldConditionFactory<>(field);
    }
}
