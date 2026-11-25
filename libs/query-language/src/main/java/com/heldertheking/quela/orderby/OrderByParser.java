package com.heldertheking.quela.orderby;

import org.jspecify.annotations.Nullable;

import com.heldertheking.quela.FieldResolutionException;

/// Interface for parsing an order_by string into jooq [SortField]s.
///
/// Parsers should support ordering by multiple columns as described in the google api style guidelines
/// (<a href="https://google.aip.dev/132">AIP-132</a>)
///
/// Examples:
///
///   - _shift_date_ and _shift_date asc_ orders elements by shift_date in ascending order
///   - _shift_date desc_ orders elements by shift_date in descending order
///   - _family_name, given_name_ orders elements with priority of familiy_name and then by given_name
public interface OrderByParser {
    /// transforms an order by string into a [OrderByCondition] that contains a list of jooq [SortField]s
    /// that result in a **stable sort**.
    ///
    /// To ensure that the returned sort fields lead to a stable sort, implementations may add an
    /// arbitrary discriminator field as last element to the list. If that field is unique the sort is
    /// guaranteed to be stable.
    ///
    /// Ususally, the id column is a good candidate for such a discriminator field
    ///
    /// @param orderBy the order by string
    /// @return a collection of sort fields that can be passed to an orderBy method in a jooq query
    /// @throws OrderByParseException thrown if the order by string cannot be parsed because it is
    ///         syntactically incorrect
    /// @throws FieldResolutionException thrown if the order by string cannot be parsed because it
    ///         references a field that does not exist
    OrderByCondition parse(@Nullable String orderBy) throws OrderByParseException, FieldResolutionException;
}
