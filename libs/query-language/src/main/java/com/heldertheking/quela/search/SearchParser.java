package com.heldertheking.quela.search;

import org.jooq.Condition;
import org.jspecify.annotations.Nullable;

public interface SearchParser {
    /// parse a query string coming from an untrusted into a condition.
    /// Implementors need to make sure that no sql injection attacks are possible. Simply taking the sql fragment
    /// and convert it into a [Condition] is a bad idea.
    ///
    /// @param query a query using a well-defined language
    /// @return a jooq condition that can be applied to any query
    Condition parse(@Nullable String query) throws QueryParseException;
}
