package com.heldertheking.quela;

import org.jooq.Field;

/// A field resolver is responsible for resolving a field from a fully qualified path.
///
/// It serves as a mapping layer between a frontend data model and a persistence model
public interface FieldResolver {
    /// given the full path to the property, an implementation should return the table field that corresponds to the field
    ///
    /// @param path fully qualified path like this.availability.shift_date
    /// @return a table field coming from generated jooq code
    <T> FieldDefinition<T> resolveField(String path) throws FieldResolutionException;

    <T> SortableFieldDefinition<T> resolveSortableField(String path) throws FieldResolutionException;

    /// A field definition contains the field, the propertyParser of the field and if the field is sortable
    /// @param propertyParser    the propertyParser of the field
    /// @param conditionFactory  build a condition based on a value
    /// @param <T>
    record FieldDefinition<T>(QueryFieldParser<T> propertyParser, ConditionFactory<T> conditionFactory) {
    }

    /// A field definition contains the field, the propertyParser of the field and if the field is sortable
    /// @param propertyParser    the propertyParser of the field
    /// @param conditionFactory  build a condition based on a value
    /// @param <T>
    record SortableFieldDefinition<T>(QueryFieldParser<T> propertyParser, ConditionFactory<T> conditionFactory, Field<T> field) {
    }
}
