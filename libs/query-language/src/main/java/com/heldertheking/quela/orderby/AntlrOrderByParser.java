package com.heldertheking.quela.orderby;

import com.heldertheking.quela.*;
import com.heldertheking.quela.FieldResolver.SortableFieldDefinition;
import com.google.common.collect.ImmutableMap;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jooq.Field;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class AntlrOrderByParser implements OrderByParser {
    private final FieldResolver fieldResolver;
    private final FieldResolver.SortableFieldDefinition<?> discriminator;

    private AntlrOrderByParser(FieldResolver fieldResolver, FieldResolver.SortableFieldDefinition<?> discriminator) {
        this.fieldResolver = fieldResolver;
        this.discriminator = discriminator;
    }

    public static BuilderStepDiscriminator newBuilder() {
        return new Builder();
    }

    @Override
    public OrderByCondition parse(@Nullable String orderBy) {
        if (orderBy == null) {
            orderBy = "";
        }
        var lexer = new OrderByLanguageLexer(CharStreams.fromString(orderBy));
        var tokens = new CommonTokenStream(lexer);
        var parser = new OrderByLanguageParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new OrderByErrorListener());
        try {
            return new OrderByVisitor(fieldResolver, discriminator).visitSortCondition(parser.sortCondition());
        } catch (InternalOrderByParseException | FieldResolutionException e) {
            throw new OrderByParseException(orderBy, "Error parsing order by clause: %s".formatted(e.getMessage()), e);
        }
    }

    /// helper interface to ensure that the discriminator field is always set before adding fields
    public interface BuilderStepDiscriminator {
        <T> Builder withDiscriminator(SingleDBFieldProperty<T, ?> queryPropertyParser, Field<T> discriminator);
    }

    public static class Builder implements BuilderStepDiscriminator {
        private final ImmutableMap.Builder<String, Field<?>> fields = ImmutableMap.builder();
        private @Nullable SortableFieldDefinition<?> discriminator;
        @Nullable
        private FieldResolver fieldResolver;

        /// Sets the discriminator field. The discriminator field is used to ensure a stable sort.
        /// If the discriminator field is unique, the sort is guaranteed to be stable.
        ///
        /// Ususally, the id column is a good candidate for such a discriminator field
        ///
        /// @param discriminator field to use as discriminator
        @Override
        public <T> Builder withDiscriminator(SingleDBFieldProperty<T, ?> queryPropertyParser, Field<T> discriminator) {
            this.discriminator = new FieldResolver.SortableFieldDefinition<>(queryPropertyParser.getQueryPropertyParser(), queryPropertyParser.conditionFactoryForField(discriminator), discriminator);
            return this;
        }

        /// defines the field resolver that should be used to parse order_by clauses.
        ///
        /// The [FieldResolver] is used to map from frontend propery names to database property names
        public Builder withFieldResolver(FieldResolver fieldResolver) {
            this.fieldResolver = fieldResolver;
            return this;
        }

        public AntlrOrderByParser build() {
            Objects.requireNonNull(discriminator, "discriminator must not be null");
            Objects.requireNonNull(fieldResolver, "fieldResolver must not be null");
            return new AntlrOrderByParser(fieldResolver, discriminator);
        }
    }
}
