package com.heldertheking.quela.search;

import com.heldertheking.quela.FieldResolver;
import com.heldertheking.quela.QueryFieldParser;
import com.heldertheking.quela.QueryLanguageBaseVisitor;
import com.heldertheking.quela.QueryLanguageParser;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.math.BigDecimal;
import java.util.List;

class JooqQuelaVisitor extends QueryLanguageBaseVisitor<Field<?>> {
    private final FieldResolver fieldResolver;

    public JooqQuelaVisitor(FieldResolver fieldResolver) {
        this.fieldResolver = fieldResolver;
    }

    private static String stripQuotes(QueryLanguageParser.LiteralContext literal) {
        var value = literal.getText();
        if (literal instanceof QueryLanguageParser.StringLiteralContext b) {
            value = b.STRING().getText().substring(1, b.STRING().getText().length() - 1);
        }
        return value;
    }

    @Override
    public Condition visitQuery(QueryLanguageParser.QueryContext ctx) {
        return visitAndCondition(ctx.andCondition());
    }

    @Override
    public Condition visitAndCondition(QueryLanguageParser.AndConditionContext ctx) {
        return ctx.orCondition().stream()
                .map(this::visitOrCondition)
                .reduce(DSL.noCondition(), Condition::and);
    }

    @Override
    public Condition visitOrCondition(QueryLanguageParser.OrConditionContext ctx) {
        return ctx.notCondition().stream()
                .map(this::visitNotCondition)
                .reduce(DSL.noCondition(), Condition::or);
    }

    @Override
    public Condition visitNotCondition(QueryLanguageParser.NotConditionContext ctx) {
        var childCondition = (Condition) visitCondition(ctx.condition());
        if (ctx.NOT() == null) {
            return childCondition;
        }
        return childCondition.not();
    }

    @Override
    public Field<?> visitCondition(QueryLanguageParser.ConditionContext ctx) {
        if (ctx.simpleCondition() != null) {
            return visitSimpleCondition(ctx.simpleCondition());
        } else if (ctx.inCondition() != null) {
            return visitInCondition(ctx.inCondition());
        } else if (ctx.andCondition() != null) {
            return visitAndCondition(ctx.andCondition());
        }
        throw new UnsupportedOperationException("Unknown condition propertyParser: " + ctx.getText());
    }

    @Override
    public Condition visitSimpleCondition(QueryLanguageParser.SimpleConditionContext ctx) {
        var fieldName = ctx.field().getText();
        return buildCondition(fieldName, ctx.operator(), ctx.literal());
    }

    private <T> Condition buildCondition(String fieldName, QueryLanguageParser.OperatorContext operator, QueryLanguageParser.LiteralContext literal) {
        var fieldDefinition = fieldResolver.<T>resolveField(fieldName);
        var type = fieldDefinition.propertyParser();
        var conditionFactory = fieldDefinition.conditionFactory();

        if (literal instanceof QueryLanguageParser.NullLiteralContext) {
            if (operator instanceof QueryLanguageParser.EqContext) {
                return conditionFactory.isNull();
            }
            if (operator instanceof QueryLanguageParser.NeqContext) {
                return conditionFactory.isNotNull();
            }
            throw new UnsupportedOperationException("Null literal can only be used with the equals or not-equals operator but not with %s".formatted(operator.getText()));
        }

        T fieldValue;
        try {
            fieldValue = coerceFieldValue(fieldName, type, literal);
        } catch (UnsupportedOperationException e) {
            throw new InternalQueryParseException("failed to parse search query: %s".formatted(e.getMessage()));
        } catch (Throwable _) {
            var expectedLiteralType = switch (type) {
                case QueryFieldParser.BooleanQueryFieldParser<?> bool -> "BOOLEAN";
                case QueryFieldParser.NumberQueryFieldParser<?> number -> "NUMBER";
                case QueryFieldParser.StringQueryFieldParser<?> string -> "STRING";
            };
            throw new InternalQueryParseException("failed to coerce %s value '%s' for field '%s'".formatted(expectedLiteralType, stripQuotes(literal), fieldName));
        }

        try {
            return switch (operator) {
                case QueryLanguageParser.EqContext c -> conditionFactory.eq(fieldValue);
                case QueryLanguageParser.NeqContext c -> conditionFactory.ne(fieldValue);
                case QueryLanguageParser.GtContext c -> conditionFactory.gt(fieldValue);
                case QueryLanguageParser.GteContext c -> conditionFactory.ge(fieldValue);
                case QueryLanguageParser.LtContext c -> conditionFactory.lt(fieldValue);
                case QueryLanguageParser.LteContext c -> conditionFactory.le(fieldValue);
                default -> throw new UnsupportedOperationException("Unknown operator: " + operator.getText());
            };
        } catch (UnsupportedOperatorException e) {
            throw new InternalQueryParseException("operator '%s' is not supported on field '%s'".formatted(e.getOperator(), fieldName), e);
        }
    }

    private static <T> T coerceFieldValue(String fieldName, QueryFieldParser<T> type, QueryLanguageParser.LiteralContext literal) {
        return switch (type) {
            case QueryFieldParser.BooleanQueryFieldParser<T> bool -> {
                if (literal instanceof QueryLanguageParser.BooleanLiteralContext b) {
                    yield bool.coerce(Boolean.parseBoolean(b.BOOLEAN().getText()));
                }
                throw new UnsupportedOperationException("literal '%s' is expected to be a boolean when comparing with field '%s'".formatted(stripQuotes(literal), fieldName));
            }
            case QueryFieldParser.NumberQueryFieldParser<T> number -> {
                if (literal instanceof QueryLanguageParser.NumberLiteralContext b) {
                    yield number.coerce(new BigDecimal(b.NUMBER().getText()));
                }
                throw new UnsupportedOperationException("literal '%s' is expected to be a number when comparing with field '%s'".formatted(stripQuotes(literal), fieldName));
            }
            case QueryFieldParser.StringQueryFieldParser<T> string -> {
                if (literal instanceof QueryLanguageParser.StringLiteralContext b) {
                    yield string.coerce(b.STRING().getText().substring(1, b.STRING().getText().length() - 1));
                }
                throw new UnsupportedOperationException("literal '%s' is expected to be a string when comparing with field '%s'".formatted(stripQuotes(literal), fieldName));
            }
        };
    }

    @Override
    public Condition visitInCondition(QueryLanguageParser.InConditionContext ctx) {
        var fieldName = ctx.field().getText();
        return buildInCondition(fieldName, ctx.literal());
    }

    private <T> Condition buildInCondition(String fieldName, List<QueryLanguageParser.LiteralContext> literal) {
        var fieldDefinition = fieldResolver.<T>resolveField(fieldName);
        final var conditionFactory = fieldDefinition.conditionFactory();
        var type = fieldDefinition.propertyParser();
        return conditionFactory
                .in(literal.stream().map(l -> coerceFieldValue(fieldName, type, l)).toList());
    }
}
