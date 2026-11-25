package com.heldertheking.quela.search;

import java.util.Objects;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

import com.heldertheking.quela.FieldResolutionException;
import com.heldertheking.quela.FieldResolver;
import com.heldertheking.quela.QueryLanguageLexer;
import com.heldertheking.quela.QueryLanguageParser;

public final class AntlrSearchParser implements SearchParser {
    private final FieldResolver fieldResolver;

    private AntlrSearchParser(FieldResolver fieldResolver) {
        this.fieldResolver = fieldResolver;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public Condition parse(@Nullable String query) throws QueryParseException {
        if (query == null || query.isBlank()) {
            return DSL.noCondition();
        }
        var lexer = new QueryLanguageLexer(CharStreams.fromString(query));
        var tokens = new CommonTokenStream(lexer);
        var parser = new QueryLanguageParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new ErrorListener());
        try {
            var tree = parser.query();
            var visitor = new JooqQuelaVisitor(fieldResolver);
            return DSL.condition(visitor.visitQuery(tree));
        } catch (InternalQueryParseException e) {
            if (e.getMessage() == null) {
                throw new QueryParseException("failed to parse search query", e, query);
            }
            throw new QueryParseException(e.getMessage(), e, query);
        } catch (FieldResolutionException e) {
            throw new QueryParseException(
                    "failed to resolve field '%s' in search query".formatted(e.getField()), e, query);
        } catch (Throwable e) {
            throw new QueryParseException("failed to parse search query", e, query);
        }
    }

    public static class Builder {
        @Nullable private FieldResolver fieldResolver;

        public Builder withFieldResolver(FieldResolver fieldResolver) {
            this.fieldResolver = fieldResolver;
            return this;
        }

        public AntlrSearchParser build() {
            Objects.requireNonNull(fieldResolver, "fieldResolver must not be null");
            return new AntlrSearchParser(fieldResolver);
        }
    }

    // possible property types
    // Boolean
    // Byte
    // Double
    // Integer
    // LocalDate
    // LocalDateTime
    // Long
    // String
    // byte[]
}
