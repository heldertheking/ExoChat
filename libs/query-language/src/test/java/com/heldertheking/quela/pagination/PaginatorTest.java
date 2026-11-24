package com.heldertheking.quela.pagination;

import com.heldertheking.quela.FieldResolver;
import com.heldertheking.quela.FieldResolverImpl;
import com.heldertheking.quela.orderby.AntlrOrderByParser;
import com.heldertheking.quela.orderby.OrderByCondition;
import com.heldertheking.quela.orderby.OrderByParser;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.heldertheking.quela.QueryFieldParser.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaginatorTest {
    DSLContext dsl = DSL.using(SQLDialect.MARIADB);
    FieldResolver fieldResolver = FieldResolverImpl.newBuilder()
            .addSortableField(STRING, "given_name", DSL.field("db_given_name", String.class))
            .addSortableField(STRING, "family_name", DSL.field("db_family_name", String.class))
            .addSortableField(LOCAL_DATE_TIME.atZone(ZoneId.of("Europe/Zurich")), "last_login", DSL.field("db_last_login", LocalDateTime.class))
            .build();
    OrderByParser orderByParser = AntlrOrderByParser.newBuilder()
            .withDiscriminator(LONG, DSL.field("id", Long.class))
            .withFieldResolver(fieldResolver)
            .build();

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class When_stepping_through_pages_without_any_orderBy_clause {
        PaginationRequest request = new TestPaginationRequestImpl("test-page", null, OrderByCondition.forDiscriminator(LONG, DSL.field("id", Long.class)), "something = 'abc'");
        String tokenString;
        Paginator paginator = new PaginatorImpl(new TestTokenSerializer());

        @Test
        @Order(1)
        void expect_paginator_to_return_empty_condition_on_first_page_token() {
            var token = paginator.parse(request);
            assertThat(token.getCursorCondition()).isEqualTo(DSL.noCondition());

            // produce page token to fetch second page
            var record = mock(Record.class);
            when(record.get(DSL.field("id", Long.class))).thenReturn(1753L);
            tokenString = paginator.nextPage(token, record);
        }

        @Test
        @Order(2)
        void expect_paginator_to_return_condition_on_second_page_access_and_produce_token_for_third_page() {
            request = new TestPaginationRequestImpl(request.pageType(), tokenString, OrderByCondition.forDiscriminator(LONG, DSL.field("id", Long.class)), "something = 'abc'");
            var token = paginator.parse(request);

            assertThat(dsl.render(token.getCursorCondition())).isEqualTo("id > ?");
            assertThat(dsl.extractBindValues(token.getCursorCondition())).containsExactly(1753L);

            var record = mock(Record.class);
            when(record.get(DSL.field("id", Long.class))).thenReturn(2430L);
            tokenString = paginator.nextPage(token, record);
        }

        @Test
        @Order(3)
        void expect_paginator_to_return_condition_on_third_page_access_and_produce_token_for_third_page() {
            request = new TestPaginationRequestImpl(request.pageType(), tokenString, OrderByCondition.forDiscriminator(LONG, DSL.field("id", Long.class)), "something = 'abc'");
            var token = paginator.parse(request);

            assertThat(dsl.render(token.getCursorCondition())).isEqualTo("id > ?");
            assertThat(dsl.extractBindValues(token.getCursorCondition())).containsExactly(2430L);

            var record = mock(Record.class);
            when(record.get(DSL.field("id", Long.class))).thenReturn(4231L);
            tokenString = paginator.nextPage(token, record);
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class When_stepping_through_pages_with_orderBy_clause {
        PaginationRequest request;
        Paginator paginator = new PaginatorImpl(new TestTokenSerializer());
        String tokenString;
        private LocalDateTime lastLoginDateTime;

        @Test
        @Order(1)
        void expect_paginator_to_return_empty_condition_on_first_page_token() {
            var orderByCondition = orderByParser.parse("family_name, given_name desc, last_login");
            request = new TestPaginationRequestImpl("TestType", "", orderByCondition, "a > 25'");
            var token = paginator.parse(request);
            assertThat(token.getCursorCondition()).isEqualTo(DSL.noCondition());

            // produce page token to fetch second page
            lastLoginDateTime = LocalDateTime.of(2025, 1, 10, 15, 0, 0);
            var record = mock(Record.class);
            doThrow(new IllegalArgumentException("MOCKED: field not found")).when(record).get(any(Field.class));
            doReturn(1753L).when(record).get(DSL.field("id", Long.class));
            doReturn("Max").when(record).get(DSL.field("db_given_name", String.class));
            doReturn("Alpha").when(record).get(DSL.field("db_family_name", String.class));
            doReturn(lastLoginDateTime).when(record).get(DSL.field("db_last_login", LocalDateTime.class));
            tokenString = paginator.nextPage(token, record);
        }

        @Test
        @Order(2)
        void expect_paginator_to_return_condition_on_second_page_access_and_produce_token_for_third_page() {
            var orderByCondition = orderByParser.parse("family_name, given_name desc, last_login");
            request = new TestPaginationRequestImpl(request.pageType(), tokenString, orderByCondition, request.filter());
            var token = paginator.parse(request);

            assertThat(dsl.render(token.getCursorCondition()))
                    .isEqualTo("(db_family_name > ? or ((db_given_name < ? or db_given_name is null) and db_family_name = ?) or (db_last_login > ? and db_family_name = ? and db_given_name = ?) or (id > ? and db_family_name = ? and db_given_name = ? and db_last_login = ?))");
            assertThat(dsl.extractBindValues(token.getCursorCondition())).containsExactly("Alpha", "Max", "Alpha", lastLoginDateTime, "Alpha", "Max", 1753L, "Alpha", "Max", lastLoginDateTime);

            lastLoginDateTime = LocalDateTime.of(2025, 1, 3, 18, 15, 35);
            var record = mock(Record.class);
            doThrow(new IllegalArgumentException("MOCKED: field not found")).when(record).get(any(Field.class));
            doReturn(39L).when(record).get(DSL.field("id", Long.class));
            doReturn("Anna").when(record).get(DSL.field("db_given_name", String.class));
            doReturn("Bauer").when(record).get(DSL.field("db_family_name", String.class));
            doReturn(lastLoginDateTime).when(record).get(DSL.field("db_last_login", LocalDateTime.class));
            tokenString = paginator.nextPage(token, record);
        }

        @Test
        @Order(3)
        void expect_paginator_to_return_condition_on_third_page_access_and_produce_token_for_third_page() {
            var orderByCondition = orderByParser.parse("family_name, given_name desc, last_login");
            request = new TestPaginationRequestImpl(request.pageType(), tokenString, orderByCondition, request.filter());
            var token = paginator.parse(request);

            assertThat(dsl.render(token.getCursorCondition()))
                    .isEqualTo("(db_family_name > ? or ((db_given_name < ? or db_given_name is null) and db_family_name = ?) or (db_last_login > ? and db_family_name = ? and db_given_name = ?) or (id > ? and db_family_name = ? and db_given_name = ? and db_last_login = ?))");
            assertThat(dsl.extractBindValues(token.getCursorCondition())).containsExactly("Bauer", "Anna", "Bauer", lastLoginDateTime, "Bauer", "Anna", 39L, "Bauer", "Anna", lastLoginDateTime);
        }
    }

    @Nested
    class When_changing_pagination_params_between_page_loads {
        Paginator paginator = new PaginatorImpl(new TestTokenSerializer());
        String tokenString;

        @BeforeEach
        void setUp() {
            var orderByCondition = orderByParser.parse("given_name");
            var request = new TestPaginationRequestImpl("test-page-type", "", orderByCondition, "something = 'abc'", List.of("pool/1"));
            var token = paginator.parse(request);
            var record = mock(Record.class);
            when(record.get(DSL.field("id", Long.class))).thenReturn(12345L);
            when(record.get(DSL.field("db_given_name", String.class))).thenReturn("Max");
            when(record.get(DSL.field("db_family_name", String.class))).thenReturn("Muster");
            tokenString = paginator.nextPage(token, record);
        }

        @ParameterizedTest
        @CsvSource(
                delimiter = '|',
                value = {
                        "test-other-type         | something = 'abc'     | given_name              | pool/1  | pagination token of other page must not be used with this page",
                        "test-page-type          | something = 'xyz'     | given_name              | pool/1  | filter must be the same as the one used to generate the token",
                        "test-page-type          | something = 'abc'     | given_name, family_name | pool/1  | orderBy must be the same as the one used to generate the token",
                        "test-page-type          | something = 'abc'     | given_name desc         | pool/1  | orderBy must be the same as the one used to generate the token",
                        "test-page-type          | something = 'abc'     | given_name              | pool/2  | orderBy must be the same as the one used to generate the token",
                }
        )
        void expect_paginator_to_throw_exception_if_params_change(String type, String filter, String orderBy, String parent, String description) {
            var orderByCondition = orderByParser.parse(orderBy);

            assertThatThrownBy(() -> {
                var request = new TestPaginationRequestImpl(type, tokenString, orderByCondition, filter, List.of(parent));
                paginator.parse(request);
            }).isInstanceOf(InvalidPageTokenException.class)
                    .hasMessage("pagination params in the page_token do not match the ones where the page_token was generated. Make sure order_by, filter and other params in the list request stay the same");
        }
    }

    @Nested
    class When_token_can_not_be_deserialized {
        PaginationTokenSerializer serializer = mock(TestTokenSerializer.class);
        Paginator paginator = new PaginatorImpl(serializer);

        @Test
        void expect_paginator_to_throw_exception_if_token_is_invalid() throws Exception {
            var error = new RuntimeException("something went wrong deserializing the token");
            when(serializer.deserialize("some-bad-token")).thenThrow(error);
            var request = new TestPaginationRequestImpl("test-page-type", "some-bad-token", OrderByCondition.forDiscriminator(LONG, DSL.field("id", Long.class)), "something = 'abc'");
            assertThatThrownBy(() -> paginator.parse(request))
                    .isInstanceOf(InvalidPageTokenException.class)
                    .hasCause(error)
                    .hasMessage("page_token can not be deserialized");
        }
    }

    @Nested
    class When_last_record_has_null_values {
        Paginator paginator = new PaginatorImpl(new TestTokenSerializer());

        private Condition nextPageCondition(String orderBy, String familyName, String givenName) {
            var request = new TestPaginationRequestImpl("test-page-type", "", orderByParser.parse(orderBy), "");
            var record = mock(Record.class);
            when(record.get(DSL.field("id", Long.class))).thenReturn(7L);
            when(record.get(DSL.field("db_family_name", String.class))).thenReturn(familyName);
            when(record.get(DSL.field("db_given_name", String.class))).thenReturn(givenName);
            var tokenString = paginator.nextPage(paginator.parse(request), record);
            var nextRequest = new TestPaginationRequestImpl(request.pageType(), tokenString, request.orderByCondition(), request.filter());
            return paginator.parse(nextRequest).getCursorCondition();
        }

        @Test
        void expect_non_null_rows_and_same_null_rows_to_follow_on_ascending_column() {
            assertThat(dsl.render(nextPageCondition("family_name", null, null)))
                    .isEqualTo("(db_family_name is not null or (id > ? and db_family_name is null))");
        }

        @Test
        void expect_only_same_null_rows_to_follow_on_descending_column() {
            assertThat(dsl.render(nextPageCondition("family_name desc", null, null)))
                    .isEqualTo("((id < ? or id is null) and db_family_name is null)");
        }

        @Test
        void expect_null_rows_to_follow_non_null_value_on_descending_column() {
            assertThat(dsl.render(nextPageCondition("family_name desc", "Alpha", null)))
                    .isEqualTo("(db_family_name < ? or db_family_name is null or ((id < ? or id is null) and db_family_name = ?))");
        }

        @Test
        void expect_is_null_to_be_used_for_null_values_in_leading_columns() {
            assertThat(dsl.render(nextPageCondition("family_name, given_name desc", null, "Max")))
                    .isEqualTo("(db_family_name is not null or ((db_given_name < ? or db_given_name is null) and db_family_name is null) or ((id < ? or id is null) and db_family_name is null and db_given_name = ?))");
        }
    }

    /// Executes the generated conditions against a real database to verify that stepping through the pages returns all
    /// rows exactly once. Like MariaDB, H2 sorts NULLs as the smallest value by default.
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class When_stepping_through_pages_with_null_values_in_sort_columns {
        record Row(long id, @Nullable String a, @Nullable Integer b) {
        }

        final Field<Long> idField = DSL.field(DSL.name("id"), Long.class);
        final Field<String> aField = DSL.field(DSL.name("a"), String.class);
        final Field<Integer> bField = DSL.field(DSL.name("b"), Integer.class);
        final Table<Record> table = DSL.table(DSL.name("rows"));

        // contains a block of rows with NULL in both columns that spans multiple pages for most page sizes
        final List<Row> rows = List.of(
                new Row(1, "x", 2),
                new Row(2, null, null),
                new Row(3, "y", null),
                new Row(4, null, 1),
                new Row(5, "x", null),
                new Row(6, null, null),
                new Row(7, "x", 1),
                new Row(8, null, null),
                new Row(9, "y", 3),
                new Row(10, null, 2),
                new Row(11, null, null),
                new Row(12, null, null)
        );

        final OrderByParser parser = AntlrOrderByParser.newBuilder()
                .withDiscriminator(LONG, idField)
                .withFieldResolver(FieldResolverImpl.newBuilder()
                        .addSortableField(STRING, "a", aField)
                        .addSortableField(INTEGER, "b", bField)
                        .build())
                .build();

        Connection connection;
        DSLContext h2;

        @BeforeAll
        void setUp() throws SQLException {
            connection = DriverManager.getConnection("jdbc:h2:mem:paginator-test");
            h2 = DSL.using(connection, SQLDialect.H2);
            h2.createTable(table)
                    .column(idField, SQLDataType.BIGINT.notNull())
                    .column(aField, SQLDataType.VARCHAR(10))
                    .column(bField, SQLDataType.INTEGER)
                    .execute();
            rows.forEach(row -> h2.insertInto(table).columns(idField, aField, bField).values(row.id(), row.a(), row.b()).execute());
        }

        @AfterAll
        void tearDown() throws SQLException {
            connection.close();
        }

        @ParameterizedTest
        @CsvSource(
                delimiter = '|',
                value = {
                        "a           | 1", "a           | 2", "a           | 3", "a           | 5", "a           | 12",
                        "a desc      | 1", "a desc      | 2", "a desc      | 3", "a desc      | 5", "a desc      | 12",
                        "a, b        | 1", "a, b        | 2", "a, b        | 3", "a, b        | 5", "a, b        | 12",
                        "a desc, b   | 1", "a desc, b   | 2", "a desc, b   | 3", "a desc, b   | 5", "a desc, b   | 12",
                        "a, b desc   | 1", "a, b desc   | 2", "a, b desc   | 3", "a, b desc   | 5", "a, b desc   | 12",
                        "a desc, b desc | 1", "a desc, b desc | 2", "a desc, b desc | 3", "a desc, b desc | 5", "a desc, b desc | 12",
                }
        )
        void expect_all_rows_to_be_returned_exactly_once_in_order(String orderBy, int pageSize) {
            assertThat(fetchAllPages(orderBy, pageSize)).containsExactlyElementsOf(expectedIds(orderBy));
        }

        private List<Long> fetchAllPages(String orderBy, int pageSize) {
            var paginator = new PaginatorImpl(new TestTokenSerializer());
            var orderByCondition = parser.parse(orderBy);
            var ids = new ArrayList<Long>();
            var pageToken = "";
            do {
                var request = new TestPaginationRequestImpl("test-page-type", pageToken, orderByCondition, "", List.of(), pageSize);
                var cursor = paginator.parse(request);
                var page = h2.select(List.of(idField, aField, bField))
                        .from(table)
                        .where(cursor.getCursorCondition())
                        .orderBy(orderByCondition.sortFields())
                        .limit(pageSize)
                        .fetch();
                ids.addAll(page.getValues(idField));
                pageToken = cursor.nextPage(page).getPageToken();
            } while (!pageToken.isEmpty());
            return ids;
        }

        /// Computes the expected order independently of the database: NULLs first on ascending and last on descending columns,
        /// ties by id in the direction of the last sort field
        private List<Long> expectedIds(String orderBy) {
            var comparator = Comparator.comparing(Row::a, nullsSmallest(orderBy.startsWith("a desc")));
            if (orderBy.contains("b")) {
                comparator = comparator.thenComparing(Row::b, nullsSmallest(orderBy.endsWith("b desc")));
            }
            var byId = Comparator.comparing(Row::id);
            return rows.stream().sorted(comparator.thenComparing(orderBy.endsWith("desc") ? byId.reversed() : byId)).map(Row::id).toList();
        }

        private <T extends Comparable<T>> Comparator<@Nullable T> nullsSmallest(boolean descending) {
            var ascending = Comparator.<@Nullable T>nullsFirst(Comparator.naturalOrder());
            return descending ? ascending.reversed() : ascending;
        }
    }

    @Test
    void expect_helpful_error_message_if_order_by_field_is_not_selected_in_record() {
        var request = new TestPaginationRequestImpl("test-page-type", "", orderByParser.parse("family_name desc"), "");
        var paginator = new PaginatorImpl(new TestTokenSerializer());
        var token = paginator.parse(request);
        var record = mock(Record.class);
        doThrow(new IllegalArgumentException("MOCKED: field not found")).when(record).get(any(Field.class));
        doReturn(12345L).when(record).get(DSL.field("id", Long.class));
        assertThatThrownBy(() -> paginator.nextPage(token, record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The last record must have a value for all order by fields but no value for field db_family_name was found. Make sure the column is selected in the query");
    }

    @Test
    void expect_nextPage_to_return_empty_token_if_called_with_null_as_last_value() {
        var paginator = new PaginatorImpl(new TestTokenSerializer());
        var pageToken = new PageCursor("some-token", DSL.noCondition(), new TestPaginationRequestImpl("test-type", "", OrderByCondition.forDiscriminator(LONG, DSL.field("id", Long.class)), ""), paginator, false);
        var nextPage = pageToken.nextPage((Record) null);
        assertThat(nextPage).extracting("pageToken", "isLastPage").containsExactly("", true);
    }
}