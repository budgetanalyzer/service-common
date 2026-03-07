# Add Reusable PagedResponse to service-common

## Context

The immediate need is knowing if any transactions exist for a given currency. The broader need is pagination for search APIs (e.g., `GET /transactions`). Spring Data already provides `Page<T>` and `Pageable`, and our `SoftDeleteOperations` already has paginated query methods — but returning raw `Page<T>` from controllers leaks Spring internals (`pageable`, `offset`, `paged`, `unpaged`, `sort` objects) into the API contract.

The solution: a clean `PagedResponse<T>` record in service-common that wraps `Page<T>` and exposes only what clients need.

**Note on count-only queries:** Spring's `PageRequest` requires `size >= 1`, so `size=0` won't work for count-only. The `countActive(Specification)` method already exists in `SoftDeleteOperations` — consumer services should use that directly for count endpoints. No `CountResponse` wrapper needed for a single `long`.

## What Changes

### 1. Create `PagedResponse.java`

**Path:** `service-web/src/main/java/org/budgetanalyzer/service/api/PagedResponse.java`

A generic Java record with `@Schema` annotations, alongside existing `ApiErrorResponse`:

```java
@Schema(description = "Paginated response wrapper")
public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last,
    boolean empty) {

  public static <T> PagedResponse<T> from(Page<T> page) { ... }
}
```

**Fields:** `content`, `page` (zero-based), `size`, `totalElements`, `totalPages`, `first`, `last`, `empty`

**Why a record not a class:** `ApiErrorResponse` uses a builder because it has optional fields with complex construction. `PagedResponse` always has all fields populated from a `Page` — a record with a static factory `from()` is the right fit.

**Dependency note:** `spring-boot-starter-data-jpa` is `compileOnly` in service-web, so the `Page` import compiles but isn't forced as a transitive dependency. Any consumer calling `PagedResponse.from()` already has Spring Data JPA.

### 2. Create `PagedResponseTest.java`

**Path:** `service-web/src/test/java/org/budgetanalyzer/service/api/PagedResponseTest.java`

Unit tests using `PageImpl` (available via `testImplementation` of spring-data-jpa). Follow existing test patterns from `FieldErrorTest.java` — `@DisplayName`, `var`, static JUnit assertions.

**Test cases:**
- Create from Page with content (verify all 8 fields)
- Create from empty Page (empty=true, first=true, last=true, totalElements=0)
- First page of multiple (first=true, last=false)
- Last page of multiple (first=false, last=true)
- Middle page (first=false, last=false)
- Single page (first=true, last=true)
- Content list preserved correctly
- Canonical constructor works directly (without Page)

## Files to Create
- `service-web/src/main/java/org/budgetanalyzer/service/api/PagedResponse.java`
- `service-web/src/test/java/org/budgetanalyzer/service/api/PagedResponseTest.java`

## Files to Reference (no changes)
- `service-web/src/main/java/org/budgetanalyzer/service/api/ApiErrorResponse.java` — `@Schema` annotation style
- `service-web/src/main/java/org/budgetanalyzer/service/api/FieldError.java` — static factory `of()` pattern
- `service-web/src/test/java/org/budgetanalyzer/service/api/FieldErrorTest.java` — test conventions
- `service-core/src/main/java/org/budgetanalyzer/core/repository/SoftDeleteOperations.java` — already has `findAllActive(Pageable)`, `findAllActive(Spec, Pageable)`, `countActive(Spec)`
- `service-web/build.gradle.kts` — no changes needed, dependencies already in place

## What is NOT in Scope
- No changes to `build.gradle.kts` (dependencies already present)
- No `CountResponse` class (use `countActive()` directly, return `long`)
- No changes to `SoftDeleteOperations` (already pagination-ready)
- No reactive-specific code (record works with both stacks)
- No consumer service changes (separate work)

## Consumer Adoption Pattern (reference)
```java
// Paginated search
@GetMapping
public PagedResponse<TransactionResponse> search(Pageable pageable) {
  return PagedResponse.from(service.search(filter, pageable).map(TransactionResponse::from));
}

// Count-only (uses existing SoftDeleteOperations.countActive)
@GetMapping("/count")
public long countByCurrency(@RequestParam String currencyCode) {
  return transactionRepository.countActive(byCurrency(currencyCode));
}
```

## Verification
```bash
./gradlew clean spotlessApply
./gradlew clean build
```
