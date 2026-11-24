package com.heldertheking.quela;

import org.jooq.Field;

public interface FieldResolverBuilder {

    <T> FieldResolverBuilder addField(QueryFieldParser<T> propertyParser, String path, ConditionFactory<T> conditionFactory);
    <T> FieldResolverBuilder addField(SingleDBFieldProperty<T, ?> type, String path, Field<T> field, boolean sortable);

    default <T> FieldResolverBuilder addField(SingleDBFieldProperty<T, ?> type, String path, Field<T> field) {
        return addField(type, path, field, false);
    }

    default <T> FieldResolverBuilder addSortableField(SingleDBFieldProperty<T, ?> type, String path, Field<T> field) {
        return addField(type, path, field, true);
    }

    FieldResolver build();
}
