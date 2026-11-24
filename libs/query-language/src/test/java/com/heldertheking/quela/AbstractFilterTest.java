package com.heldertheking.quela;

import com.heldertheking.quela.orderby.AntlrOrderByParser;
import com.heldertheking.quela.orderby.OrderByParser;
import com.heldertheking.quela.pagination.Paginator;
import com.heldertheking.quela.pagination.PaginatorImpl;
import com.heldertheking.quela.pagination.TestTokenSerializer;
import com.heldertheking.quela.search.AntlrSearchParser;
import com.heldertheking.quela.search.SearchParser;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

class AbstractFilterTest {
    static FieldResolver fieldResolver = FieldResolverImpl.newBuilder()
            .addField(QueryFieldParser.STRING, "name", DSL.field("name", String.class))
            .addSortableField(QueryFieldParser.STRING, "sortit", DSL.field("sortit", String.class))
            .build();
    static Paginator paginator = new PaginatorImpl(new TestTokenSerializer());
    static OrderByParser orderByParser = AntlrOrderByParser.newBuilder()
            .withDiscriminator(QueryFieldParser.LONG, DSL.field("id", Long.class))
            .withFieldResolver(fieldResolver)
            .build();
    static SearchParser searchParser = AntlrSearchParser.newBuilder()
            .withFieldResolver(fieldResolver)
            .build();
    DSLContext dslContext = DSL.using(SQLDialect.MARIADB);

    static class TestFilter extends AbstractFilter {
        TestFilter(Condition baseCondition, PaginationParams params) {
            super(baseCondition, params);
        }

        static Builder newBuilder() {
            return new Builder(paginator, orderByParser, searchParser);
        }

        static class Builder extends AbstractFilter.Builder<Builder, TestFilter> {

            protected Builder(Paginator paginator, OrderByParser orderByParser, SearchParser searchParser) {
                super(paginator, orderByParser, searchParser, DSL.field("a", Integer.class).eq(15));
            }

            @Override
            protected List<String> additionalPaginationParams() {
                return List.of();
            }

            @Override
            protected int getDefaultPageSize() {
                return 37;
            }

            @Override
            protected int maxPageSize() {
                return 147;
            }

            @Override
            public TestFilter build() {
                return new TestFilter(baseCondition, buildPaginationParams());
            }
        }
    }

    @Nested
    class Working_with_simple_filters {
        @ParameterizedTest
        @CsvSource(
                value = {
                        "-50, 1",
                        "0, 37", // expect default value on 0 (empty value in protobuf)
                        "1, 1",
                        "2, 2",
                        "147, 147",
                        "148, 147"
                }
        )
        void expect_page_size_to_be_coerced_into_valid_range(int pageSize, int expectedPageSize) {
            var filter = TestFilter.newBuilder()
                    .withPageSize(pageSize)
                    .build();
            assertThat(filter.getPageSize()).isEqualTo(expectedPageSize);
        }

        @ParameterizedTest
        @CsvSource(
                delimiter = '|',
                value = {
                        "             |             | 100   | (a = 15 and id > 1753)",
                        "name = 'bla' | sortit      | b,100 | (a = 15 and name = 'bla' and (sortit > 'some-value' or (id > 1753 and sortit = 'some-value')))",
                        "name = 'foo' | sortit desc | b,100 | (a = 15 and name = 'foo' and (sortit < 'some-value' or sortit is null or ((id < 1753 or id is null) and sortit = 'some-value')))",
                }
        )
        void expect_merged_condition_to_contain_pagination_filter_and_base_condition(String filterStr, String orderBy, String lastPageValues, String expectedCondition) throws Exception {
            var filter = TestFilter.newBuilder()
                    .withFilter(filterStr)
                    .withOrderBy(orderBy)
                    .build();
            var record = mock(Record.class);
            doThrow(new IllegalArgumentException("MOCKED: field not found")).when(record).get(any(Field.class));
            doReturn(1753L).when(record).get(DSL.field("id", Long.class));
            doReturn("some-value").when(record).get(DSL.field("sortit", String.class));
            var nextPageToken = paginator.nextPage(filter.params.pageCursor(), record);

            filter = TestFilter.newBuilder()
                    .withFilter(filterStr)
                    .withOrderBy(orderBy)
                    .withPageToken(nextPageToken)
                    .build();
            var mergedCondition = filter.getMergedCondition();
            assertThat(dslContext.renderInlined(mergedCondition))
                    .isEqualTo(expectedCondition);

        }
    }
}