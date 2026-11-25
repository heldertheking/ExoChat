package com.heldertheking.quela;

import org.jooq.Field;

import com.google.common.collect.ImmutableMap;

public final class FieldResolverImpl implements FieldResolver {
    private final ImmutableMap<String, FieldDefinition<?>> fields;
    private final ImmutableMap<String, SortableFieldDefinition<?>> sortableFields;

    private FieldResolverImpl(
            ImmutableMap<String, FieldDefinition<?>> queryFields,
            ImmutableMap<String, SortableFieldDefinition<?>> sortableFields) {
        this.fields = queryFields;
        this.sortableFields = sortableFields;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> FieldDefinition<T> resolveField(String path) throws FieldResolutionException {
        if (fields.containsKey(path)) {
            return (FieldDefinition<T>) fields.get(path);
        }
        throw new FieldResolutionException(path);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> SortableFieldDefinition<T> resolveSortableField(String path) throws FieldResolutionException {
        if (sortableFields.containsKey(path)) {
            return (SortableFieldDefinition<T>) sortableFields.get(path);
        }
        if (fields.containsKey(path)) {
            throw new FieldResolutionException(
                    "field `" + path + "` is not sortable and must not be used in an order_by clause", path);
        } else {
            throw new FieldResolutionException(path);
        }
    }

    public static FieldResolverBuilder newBuilder() {
        return new Builder();
    }

    static class Builder implements FieldResolverBuilder {
        private final ImmutableMap.Builder<String, FieldDefinition<?>> fields = ImmutableMap.builder();
        private final ImmutableMap.Builder<String, SortableFieldDefinition<?>> sortableFields = ImmutableMap.builder();

        @Override
        public <T> FieldResolverBuilder addField(
                SingleDBFieldProperty<T, ?> type, String path, Field<T> field, boolean sortable) {
            fields.put(
                    path, new FieldDefinition<>(type.getQueryPropertyParser(), type.conditionFactoryForField(field)));
            if (sortable) {
                sortableFields.put(
                        path,
                        new SortableFieldDefinition<>(
                                type.getQueryPropertyParser(), type.conditionFactoryForField(field), field));
            }
            return this;
        }

        @Override
        public <T> FieldResolverBuilder addField(
                QueryFieldParser<T> propertyParser, String path, ConditionFactory<T> conditionFactory) {
            fields.put(path, new FieldDefinition<>(propertyParser, conditionFactory));
            return this;
        }

        @Override
        public FieldResolver build() {
            return new FieldResolverImpl(fields.build(), sortableFields.build());
        }
    }
}
