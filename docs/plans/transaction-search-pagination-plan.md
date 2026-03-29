# Transaction Search Pagination Plan

## Goal

Add a shared, stable pagination contract to `service-common` that can be used first by the
transaction admin search view and then by other services with large list/search endpoints.

## Constraints

- Preserve backwards compatibility. Do not change an existing endpoint from `List<T>` to a paged
  response in place.
- Follow Spring Boot and Spring Data conventions instead of inventing a parallel pagination model.
- Keep the public JSON contract stable across services.
- Make the first implementation usable for thousands of transactions without forcing a later
  rewrite of every consumer.

## Decisions Locked In

### Request Side

- Use Spring `Pageable` and `Sort` in controller methods.
- Keep Spring Boot defaults for parameter names: `page`, `size`, `sort`.
- Keep zero-based page numbering in v1.
- Set service-level page size caps with Spring Boot properties:
  - `spring.data.web.pageable.default-page-size`
  - `spring.data.web.pageable.max-page-size`
  - `spring.data.web.pageable.one-indexed-parameters`
  - `spring.data.web.sort.sort-parameter`

### Response Side

- Do not expose raw Spring `Page` or `PageImpl` as the public API contract.
- Add a shared wrapper in `service-web`, using project naming conventions:
  - `PagedResponse<T>`
  - `PageMetadataResponse`
- The wrapper should be created from a Spring `Page<T>` and serialize to stable JSON.
- Keep the item array field named `content` to align with Spring `Page#getContent()`.

### Pagination Strategy

- Use `Page<T>` for the admin transaction search view because the UI will usually need
  `totalElements` and `totalPages`.
- Do not introduce cursor pagination in v1.
- Revisit cursor or keyset pagination later if very deep page access becomes a real performance
  problem.

### Sorting Strategy

- Every paged endpoint must define a deterministic default sort.
- For transaction search, default sort should be stable and business-relevant, for example:
  - `date,DESC`
  - `id,DESC`
- Endpoint implementations should validate or constrain allowed sort fields instead of accepting
  arbitrary property names.

## Spring Best Practice Summary

- Spring Data recommends `Pageable` and `Sort` for request binding in web controllers.
- Spring Data supports `Page` and `Slice`; `Page` issues a count query while `Slice` avoids the
  total count.
- Spring Data's current guidance is that direct `PageImpl` serialization is not the recommended
  public JSON contract.
- Spring Data provides `PagedModel` as a stable DTO-style representation. In this codebase we will
  use the same principle but expose our own shared response type to keep the API contract fully
  under Budget Analyzer control.

## Proposed Response Shape

```json
{
  "content": [
    {
      "...": "TransactionResponse fields"
    }
  ],
  "metadata": {
    "page": 0,
    "size": 50,
    "numberOfElements": 50,
    "totalElements": 12437,
    "totalPages": 249,
    "first": true,
    "last": false
  }
}
```

Notes:

- Keep the response minimal in v1.
- Use `content` rather than `data` so the wrapper stays aligned with Spring pagination terms.
- Do not add links, embedded HAL structures, or duplicate sort metadata unless a concrete client
  need appears.

## Session 1: Service-Common Pagination Foundation

### Goal

Create the shared response contract and documentation in `service-common`.

### Deliverables

- Add `service-web/src/main/java/org/budgetanalyzer/service/api/PagedResponse.java`.
- Add `service-web/src/main/java/org/budgetanalyzer/service/api/PageMetadataResponse.java`.
- Add static factories such as:
  - `PagedResponse.from(Page<T> page)`
  - `PagedResponse.from(Page<S> page, Function<S, T> mapper)`
- Add OpenAPI `@Schema` annotations so consumers get usable generated docs.
- Update pagination examples in:
  - `docs/spring-boot-conventions.md`
  - `docs/common-patterns.md`

### Test Work

- Add `service-web/src/test/java/org/budgetanalyzer/service/api/PagedResponseTest.java`.
- Cover:
  - empty page
  - single page
  - multi-page metadata
  - mapped response content
  - stable JSON serialization shape

### Exit Criteria

- A service can return `PagedResponse<TransactionResponse>` without exposing raw Spring page JSON.
- Tests prove the wrapper contract is stable.
- Docs show the canonical controller/service usage pattern.

## Session 2: Transaction-Service Admin Search Adoption

### Goal

Adopt the shared pagination contract in the first real consumer: transaction admin search.

### Deliverables

- Add a paged transaction search method in `transaction-service` service layer:
  - `Page<Transaction> search(TransactionFilter filter, Pageable pageable, String userId, boolean isAdmin)`
- Reuse existing repository support:
  - `findAllActive(spec, pageable)`
- Add a new paged search endpoint instead of changing the existing list endpoint in place.
- Return `PagedResponse<TransactionResponse>`.
- Preserve current authorization behavior:
  - admins can search all active transactions
  - non-admin users remain restricted to their own transactions

### Recommended Endpoint Shape

- Preferred approach: add a new paged search endpoint and leave the current unpaged list endpoint
  unchanged for compatibility.
- Keep filter fields query-based if possible so paging and filtering stay cache- and URL-friendly.

### Test Work

- Controller tests for:
  - pagination params binding
  - default sort behavior
  - JSON response shape
  - admin vs non-admin visibility
- Service tests for:
  - repository call with `Pageable`
  - owner filter behavior
  - deterministic sorting assumptions

### Exit Criteria

- Admin UI can query a paged transaction search endpoint safely for large datasets.
- Existing consumers of the unpaged endpoint are not broken.

## Session 3: Hardening and Rollout Guardrails

### Goal

Make the initial rollout safe for future adoption by other services.

### Deliverables

- Set explicit consumer defaults in `transaction-service`, for example:
  - `spring.data.web.pageable.default-page-size=50`
  - `spring.data.web.pageable.max-page-size=100`
- Validate or constrain allowed sort fields in the transaction endpoint.
- Update transaction-service API documentation to show the paged response contract.
- Document migration guidance for other services that still return large `List<T>` payloads.

### Test Work

- Integration tests for property-driven page size caps.
- Negative-path tests for unsupported sort fields, if sort validation is added.

### Exit Criteria

- The first consumer uses page-size caps and stable sorting.
- The rollout pattern is documented well enough to repeat in other services.

## Session 4: Follow-Up for Other Services

### Goal

Reuse the shared contract across the platform without introducing service-specific pagination
shapes.

### Candidate Adoptions

- budget lists
- audit/event history endpoints
- admin search screens in other services
- any endpoint that can reasonably return thousands of rows

### Rules for Future Adoptions

- Reuse `PagedResponse<T>`.
- Keep request binding Spring-native with `Pageable`.
- Do not return raw `PageImpl`.
- Do not replace existing list endpoints in place unless the endpoint is internal and all consumers
  can be upgraded in the same lockstep release.

## Non-Goals

- Cursor pagination in v1
- HAL or HATEOAS link generation in v1
- Generic query-language abstractions
- A custom request wrapper around Spring `Pageable`

## Risks and Watch Items

- Offset pagination becomes more expensive as page offsets grow.
- Returning total counts can be expensive on some filtered queries.
- Unvalidated sort fields can cause fragile queries or poor execution plans.
- Changing the existing transaction list endpoint in place would be a breaking change.

## Source Notes

- Spring Data web support for `Pageable` and `Sort`:
  https://docs.spring.io/spring-data/data-jpa/reference/data-commons/3.2/repositories/core-extensions.html
- Spring Data guidance on `Page` vs `Slice`:
  https://docs.spring.io/spring-data/commons/docs/3.1.6/reference/html/
- Spring Data `PagedModel` as the stable DTO-style representation model:
  https://docs.spring.io/spring-data/commons/docs/3.5.x/api/org/springframework/data/web/PagedModel.html
- Spring Data warning that direct `PageImpl` serialization is not the recommended public mode:
  https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/web/config/SpringDataJackson3Configuration.PageModule.html
