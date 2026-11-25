package com.heldertheking.quela.orderby;

import java.util.Optional;
import java.util.stream.Stream;

import org.jooq.SortField;
import org.jooq.SortOrder;

import com.google.common.collect.ImmutableList;
import com.heldertheking.quela.FieldResolver;
import com.heldertheking.quela.OrderByLanguageBaseVisitor;
import com.heldertheking.quela.OrderByLanguageParser;

/// ANTLR Visitor for parsing an order_by string into a OrderByCondition. The class is internal to the
/// package and should not be used outside of it. The public interface is defined in [OrderByParser].
class OrderByVisitor extends OrderByLanguageBaseVisitor<Object> {
    private final FieldResolver fieldResolver;
    private final FieldResolver.SortableFieldDefinition<?> discriminator;

    OrderByVisitor(FieldResolver fieldResolver, FieldResolver.SortableFieldDefinition<?> discriminator) {
        this.fieldResolver = fieldResolver;
        this.discriminator = discriminator;
    }

    @Override
    public OrderByCondition visitSortCondition(OrderByLanguageParser.SortConditionContext ctx) {
        var sortFields = ctx.sortField().stream().map(this::sortField).toList();
        // The discriminator follows the direction of the last sort field: "created desc, id desc" can be read backwards
        // from an index on (created, id), "created desc, id asc" needs a sort of all matching rows.
        var order = sortFields.isEmpty()
                ? SortOrder.ASC
                : sortFields.getLast().sortField().getOrder();
        var definitionsAndSortFields =
                ImmutableList.copyOf(Stream.concat(sortFields.stream(), Stream.of(discriminator(order)))
                        .iterator());
        return new OrderByCondition(
                definitionsAndSortFields.stream()
                        .map(DefinitionAndSortField::definition)
                        .collect(ImmutableList.toImmutableList()),
                definitionsAndSortFields.stream()
                        .map(DefinitionAndSortField::sortField)
                        .collect(ImmutableList.toImmutableList()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private DefinitionAndSortField<?> discriminator(SortOrder order) {
        return new DefinitionAndSortField(
                discriminator, discriminator.field().sort(order == SortOrder.DESC ? SortOrder.DESC : SortOrder.ASC));
    }

    /// The seek condition of the paginator relies on the dialect sorting NULLs as the smallest value, as
    /// MariaDB does. NULLS FIRST / LAST is deliberately not set: MariaDB has no native support, and the
    /// expression jOOQ emulates it with prevents sorting via an index, also on nullable columns.
    private <T> DefinitionAndSortField<T> sortField(OrderByLanguageParser.SortFieldContext ctx) {
        FieldResolver.SortableFieldDefinition<T> definition = field(ctx.field());
        var order = Optional.ofNullable(ctx.order()).map(this::visitOrder).orElse(SortOrder.ASC);
        return new DefinitionAndSortField<>(definition, definition.field().sort(order));
    }

    private <T> FieldResolver.SortableFieldDefinition<T> field(OrderByLanguageParser.FieldContext ctx) {
        var fieldName = ctx.IDENTIFIER().stream()
                .map(Object::toString)
                .reduce((acc, it) -> acc + "." + it)
                .orElseThrow();
        return fieldResolver.resolveSortableField(fieldName);
    }

    @Override
    public SortOrder visitOrder(OrderByLanguageParser.OrderContext ctx) {
        return ctx.DESC() != null ? SortOrder.DESC : SortOrder.ASC;
    }

    private record DefinitionAndSortField<T>(
            FieldResolver.SortableFieldDefinition<T> definition, SortField<T> sortField) {}
}
