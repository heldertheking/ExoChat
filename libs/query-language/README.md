# libs/query-language ("quela")

Turns the `filter`, `order_by`, `page_size` and `page_token` fields of a list request (AIP-132/158/160 style) into jOOQ `Condition`s and `SortField`s, with keyset-based page tokens. It hides jOOQ from the gRPC layer.

## Pieces

| Package | What it does |
|---|---|
| `quela` | `FieldResolver` / `FieldResolverBuilder` map API field paths (`personal_data.name`) to jOOQ fields and typed parsers (`QueryFieldParser`). `AbstractFilter` is the base class for per-repository filters. |
| `quela.search` | `AntlrSearchParser` parses the filter language (`QueryLanguage.g4`) into a jOOQ `Condition`. |
| `quela.orderby` | `AntlrOrderByParser` parses `order_by` (`OrderByLanguage.g4`) into `OrderByCondition`. |
| `quela.pagination` | `Paginator`/`PaginatorImpl` build and validate page tokens (`PageCursor`), `ListPage` is the result type, `PageSpliterator` lazily walks pages. |

## Languages

- Filter: `field op literal`, ops `= != > >= < <=`, `field IN [..]`, `AND`/`OR`/`NOT` (or `~`), parentheses. Literals: `'string'` (a `*` becomes `LIKE %`), numbers, `true`/`false`, `null`. Example: `status = 'OPEN' and (priority >= 2 or owner.name = 'a*')`.
- Order by: `field [asc|desc], ...`. Only fields registered as sortable are allowed.
- Grammars live in `src/main/antlr/...` and ship inside the jar.

## Usage

1. Register fields once per resource with `FieldResolverBuilder.addField / addSortableField`.
2. Create `AntlrSearchParser`, `AntlrOrderByParser` and `PaginatorImpl(PaginationTokenSerializer)`.
3. Subclass `AbstractFilter` and its `Builder` per repository (see the Javadoc on `AbstractFilter`). Put anything that must not change between pages (e.g. a parent) into `additionalPaginationParams()`; the token checksum covers it.
4. In the repository:

```java
var filter = builder.withFilter(f).withOrderBy(o).withPageSize(n).withPageToken(t).build();
var rows = dsl.selectFrom(T).where(filter.getMergedCondition())
        .orderBy(filter.getSortFields()).limit(filter.getPageSize()).fetch();
var next = filter.getPageToken().nextPage(rows);   // PageCursor
return new ListPage<>(items, next.getPageToken());  // or ListPage.lastPage(items)
```

Page size defaults to 25, max 100 (override `getDefaultPageSize()` / `maxPageSize()` in the builder).

## Ready made parts

- `QueryFieldParser.UUID`, `STRING`, `LONG`, `DATE_TIME`, `LOCAL_DATE_TIME.atZone(zone)` and more for registering fields.
- `Base64PaginationTokenSerializer`: a stateless `PaginationTokenSerializer` (base64url of checksum and values), use it in production.

## PageSpliterator (in-memory post-filter)

If part of a filter cannot be expressed in SQL, wrap the query in a `PageSpliterator.PageFetcher` (offset based), stream it, apply the predicate and `.limit(pageSize)`. Pages are fetched only until enough rows survive. It costs one extra query to detect the end; return `Optional.empty()` for an empty page. Sequential streams only. Ported from the hospital_pool app-gateway.

## Build and test

`./gradlew :libs:query-language:test` (H2 in tests, no Docker needed). NullAway runs through the shared root configuration (see `docs/NEW_MODULE.md`). ANTLR is `antlr` scope only, consumers get just the runtime.
