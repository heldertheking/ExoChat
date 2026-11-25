package com.heldertheking.quela.orderby;

import org.jooq.Field;
import org.jooq.SortField;
import org.jspecify.annotations.Nullable;

import com.google.common.collect.ImmutableList;
import com.heldertheking.quela.FieldResolver;
import com.heldertheking.quela.SingleDBFieldProperty;

/// Represents a parsed order by condition as parsed by an [OrderByParser].
/// The last column in an OrderByCondition must always be a discriminator field to ensure a stable sort.
/// The discriminator field must be unique to ensure a stable sort.
///
/// @param fieldDefinitions contains the field definitions that are used in the order by condition. The order of
/// the fields corresponds to the order of fields is the same as in the original orderBy string and is the same as in
/// sortFields.
/// @param sortFields       contains the sort fields that are used in the order by condition of a jooq query
///                         The order of the fields corresponds to the ones in fieldDefinitions.
public record OrderByCondition(
        ImmutableList<FieldResolver.SortableFieldDefinition<?>> fieldDefinitions,
        ImmutableList<SortField<?>> sortFields) {
    public static <T> OrderByCondition forDiscriminator(
            @Nullable SingleDBFieldProperty<T, ?> type, @Nullable Field<T> field) {
        if (type == null || field == null) {
            throw new IllegalArgumentException("propertyParser and field must not be null");
        }
        var definition = new FieldResolver.SortableFieldDefinition<>(
                type.getQueryPropertyParser(), type.conditionFactoryForField(field), field);
        return new OrderByCondition(ImmutableList.of(definition), ImmutableList.of(field.asc()));
    }
}
