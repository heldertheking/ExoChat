package com.heldertheking.quela.pagination;

import com.heldertheking.quela.FieldResolver;
import com.heldertheking.quela.QueryFieldParser;
import com.google.common.hash.Hashing;
import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.SortOrder;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;


public class PaginatorImpl implements Paginator {
    private final PaginationTokenSerializer serializer;

    public PaginatorImpl(PaginationTokenSerializer serializer) {
        this.serializer = serializer;
    }

    /// Rows sorted after the given value on an ascending column. NULLs sort first, so all non-NULL rows follow a NULL.
    private static <T> Condition gtCondition(FieldResolver.SortableFieldDefinition<T> definition, @Nullable String stringValue) {
        if (stringValue == null) {
            return definition.conditionFactory().isNotNull();
        }
        var value = getValue(definition, stringValue);
        return definition.conditionFactory().gt(value);
    }

    /// Rows sorted after the given value on a descending column. NULLs sort last, so they follow every non-NULL value
    /// and nothing follows a NULL.
    ///
    /// @return the condition or null if no row can be sorted after the given value
    private static <T> @Nullable Condition ltCondition(FieldResolver.SortableFieldDefinition<T> definition, @Nullable String stringValue) {
        if (stringValue == null) {
            return null;
        }
        var value = getValue(definition, stringValue);
        return definition.conditionFactory().lt(value).or(definition.conditionFactory().isNull());
    }

    private static <T> Condition eqCondition(FieldResolver.SortableFieldDefinition<T> definition, @Nullable String stringValue) {
        if (stringValue == null) {
            return definition.conditionFactory().isNull();
        }
        var value = getValue(definition, stringValue);
        return definition.conditionFactory().eq(value);
    }

    private static <T> T getValue(FieldResolver.SortableFieldDefinition<T> definition, String stringValue) {
        return switch (definition.propertyParser()) {
            case QueryFieldParser.NumberQueryFieldParser<T> t -> t.coerce(new BigDecimal(stringValue));
            case QueryFieldParser.BooleanQueryFieldParser<T> t -> t.coerce(Boolean.parseBoolean(stringValue));
            case QueryFieldParser.StringQueryFieldParser<T> t -> t.coerce(stringValue);
        };
    }

    private static long computeChecksum(PaginationRequest request) {
        var hasher = Hashing.sipHash24(0x0619B45F32E8FB2DL, 0xD991E9FB728A3749L).newHasher()
                .putString(request.pageType(), StandardCharsets.UTF_8)
                .putByte((byte) 0)
                .putString(request.filter(), StandardCharsets.UTF_8);
        request.additionalPaginationParams()
                .forEach(param -> {
                    hasher.putByte((byte) 0);
                    hasher.putString(param, StandardCharsets.UTF_8);
                });
        request.orderByCondition().sortFields().forEach(sortField -> {
            hasher.putString(sortField.getName(), StandardCharsets.UTF_8);
            hasher.putBoolean(sortField.getOrder() == SortOrder.ASC);
        });
        return hasher.hash().asLong();
    }

    private static void validateChecksum(PaginationRequest request, long expectedChecksum) {
        var checksum = computeChecksum(request);
        if (checksum != expectedChecksum) {
            throw new InvalidPageTokenException("pagination params in the page_token do not match the ones where the page_token was generated. Make sure order_by, filter and other params in the list request stay the same");
        }
    }

    /// Builds the seek condition matching all rows sorted after the last row of the previous page.
    /// The dialect must sort NULLs as the smallest value, i.e. first on ascending and last on descending columns.
    private static Condition getCondition(PaginationRequest request, PaginationTokenSerializer.Token token) {
        var lastOrderByValues = token.values();
        var sortOrders = request.orderByCondition().sortFields();
        var orderFieldDefinitions = request.orderByCondition().fieldDefinitions();

        if (orderFieldDefinitions.size() != lastOrderByValues.size()) {
            throw new IllegalArgumentException("The size of orderByFields and lastOrderByValues must be the same");
        }

        var conditions = new ArrayList<Condition>();
        for (var i = 0; i < orderFieldDefinitions.size(); i++) {
            Condition columnCondition =
                    sortOrders.get(i).getOrder() == SortOrder.ASC ?
                            gtCondition(orderFieldDefinitions.get(i), lastOrderByValues.get(i)) :
                            ltCondition(orderFieldDefinitions.get(i), lastOrderByValues.get(i));
            if (columnCondition == null) {
                continue;
            }
            for (var j = 0; j < i; j++) {
                columnCondition = columnCondition.and(eqCondition(orderFieldDefinitions.get(j), lastOrderByValues.get(j)));
            }
            conditions.add(columnCondition);
        }
        return conditions.isEmpty() ? DSL.falseCondition() : DSL.or(conditions);
    }

    @Override
    public PageCursor parse(PaginationRequest request) {
        if (request.paginationToken() == null || request.paginationToken().isEmpty()) {
            return firstPage(request);
        }
        PaginationTokenSerializer.Token token;
        try {
            token = serializer.deserialize(request.paginationToken());
        } catch (Exception e) {
            throw new InvalidPageTokenException("page_token can not be deserialized", e);
        }
        validateChecksum(request, token.checksum());
        var condition = getCondition(request, token);
        return new PageCursor(request.paginationToken(), condition, request, this, false);
    }

    private static <T> @Nullable String serializeColumnValue(Record lastRecord, FieldResolver.SortableFieldDefinition<T> fieldDefinition) {
        try {
            var lastFieldValue = lastRecord.get(fieldDefinition.field());
            if (lastFieldValue == null) {
                return null;
            }
            return switch (fieldDefinition.propertyParser()) {
                case QueryFieldParser.BooleanQueryFieldParser<T> b ->
                        Boolean.toString(b.decoerce(lastFieldValue));
                case QueryFieldParser.NumberQueryFieldParser<T> n -> n.decoerce(lastFieldValue).toString();
                case QueryFieldParser.StringQueryFieldParser<T> s -> s.decoerce(lastFieldValue);
            };
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The last record must have a value for all order by fields but no value for field %s was found. Make sure the column is selected in the query".formatted(fieldDefinition
                    .field()
                    .getName()), e);
        }
    }

    @Override
    public String nextPage(PageCursor token, @Nullable Record lastRecord) {
        if (lastRecord == null) {
            return "";
        }
        var checksum = computeChecksum(token.getRequest());
        var orderByValues = token.getRequest().orderByCondition().fieldDefinitions().stream()
                .map(fieldDefinition -> serializeColumnValue(lastRecord, fieldDefinition))
                .toList();
        return serializer.serialize(new PaginationTokenSerializer.Token(checksum, orderByValues));
    }
}
