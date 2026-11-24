# ADR-010: Shared list-query language (quela)

**Status:** Accepted

List endpoints use AIP-style `filter`, `order_by`, `page_size` and `page_token`, handled by `libs/query-language`: ANTLR-parsed filter and ordering turned into jOOQ conditions, with keyset page tokens bound to a checksum of the request. Repositories must not parse these themselves.
