# AssertJ Assertion Standardization

## Status

Approved. In progress.

Implementation status as of 2026-04-10:
- PR 1 (`service-core`) implemented and verified.
- PR 2 (`service-web`) pending.
- PR 3 (documentation update) pending.

## Problem Statement

The codebase uses two assertion frameworks inconsistently: JUnit 5's built-in `Assertions`
(`assertEquals`, `assertTrue`, etc.) and AssertJ (`assertThat`). There are 35 test files using JUnit
assertions and 18 using AssertJ. The `testing-patterns.md` documentation uses JUnit assertions in
~20 code snippets and AssertJ in only 2, with no documented rule on which to use.

This inconsistency means:
- New tests are written in whichever style the author encounters first.
- Code reviews have no standard to enforce.
- The codebase drifts further from a single convention over time.

## Decision

Standardize on **AssertJ** for all test assertions.

### Rationale

- AssertJ is the industry standard for modern Spring Boot projects. Spring Boot, Spring Framework,
  Spring Security, Hibernate, Kafka, and Elasticsearch all use it.
- `spring-boot-starter-test` bundles AssertJ by default — it's already on the classpath.
- AssertJ's fluent API (`assertThat(actual).isEqualTo(expected)`) eliminates the argument-order
  confusion of JUnit's `assertEquals(expected, actual)`.
- AssertJ already represents the majority convention in this codebase (18 files vs 7 in
  service-core, though service-web skews toward JUnit with 28 files).

## Conversion Reference

| JUnit Assertion                        | AssertJ Equivalent                                      |
|----------------------------------------|---------------------------------------------------------|
| `assertEquals(expected, actual)`       | `assertThat(actual).isEqualTo(expected)`                |
| `assertNotEquals(expected, actual)`    | `assertThat(actual).isNotEqualTo(expected)`             |
| `assertTrue(condition)`               | `assertThat(condition).isTrue()`                        |
| `assertFalse(condition)`              | `assertThat(condition).isFalse()`                       |
| `assertNull(value)`                   | `assertThat(value).isNull()`                            |
| `assertNotNull(value)`                | `assertThat(value).isNotNull()`                         |
| `assertThrows(X.class, () -> ...)`    | `assertThatThrownBy(() -> ...).isInstanceOf(X.class)`   |
| `assertTrue(opt.isPresent())`         | `assertThat(opt).isPresent()`                           |
| `assertFalse(opt.isPresent())`        | `assertThat(opt).isEmpty()`                             |
| `assertTrue(collection.isEmpty())`    | `assertThat(collection).isEmpty()`                      |

Import change per file:
```java
// Remove
import static org.junit.jupiter.api.Assertions.*;

// Add
import static org.assertj.core.api.Assertions.*;
```

## Implementation Plan

### PR 1: service-core (7 files)

**Branch**: `chore/assertj-standardization-core`

#### Step 1: Migrate test files

Convert JUnit assertions to AssertJ in the following 7 files:

- `core/config/SecurityContextAuditorAwareTest.java`
- `core/domain/SoftDeletableEntityTest.java`
- `core/csv/CsvDataTest.java`
- `core/csv/CsvRowTest.java`
- `core/csv/impl/OpenCsvParserTest.java`
- `core/domain/AuditableEntityTest.java`
- `core/domain/SoftDeleteListenerTest.java`

For each file:
1. Replace `import static org.junit.jupiter.api.Assertions.*` with
   `import static org.assertj.core.api.Assertions.*`
2. Convert each assertion call per the conversion table above.
3. Prefer AssertJ-idiomatic forms where possible (e.g., `assertThat(opt).isPresent()` instead of
   `assertThat(opt.isPresent()).isTrue()`).

#### Step 2: Verify

Run `./gradlew service-core:test` — all tests must pass.

### PR 2: service-web (28 files)

**Branch**: `chore/assertj-standardization-web`

#### Step 1: Migrate test files

Convert JUnit assertions to AssertJ in the following 28 files:

- `security/ClaimsHeaderSecurityConfigTest.java`
- `servlet/api/ServletApiExceptionHandlerTest.java`
- `reactive/http/ReactiveHttpLoggingConfigTest.java`
- `api/ApiExceptionHandlerTest.java`
- `reactive/api/ReactiveApiExceptionHandlerTest.java`
- `reactive/api/ReactiveErrorWebExceptionHandlerAutoConfigurationTest.java`
- `reactive/api/ReactiveErrorWebExceptionHandlerTest.java`
- `servlet/http/ContentLoggingUtilTest.java`
- `servlet/http/HttpLoggingFilterTest.java`
- `reactive/http/ReactiveHttpLoggingFilterTest.java`
- `servlet/http/HttpLoggingPropertiesTest.java`
- `security/ReactiveClaimsHeaderSecurityConfigTest.java`
- `api/PagedResponseTest.java`
- `servlet/http/CorrelationIdFilterTest.java`
- `reactive/http/CachedBodyServerHttpRequestDecoratorTest.java`
- `reactive/http/CachedBodyServerHttpResponseDecoratorTest.java`
- `reactive/http/ReactiveCorrelationIdFilterTest.java`
- `api/ApiErrorTypeTest.java`
- `servlet/http/HttpLoggingConfigTest.java`
- `api/ApiErrorResponseTest.java`
- `api/FieldErrorTest.java`
- `config/BaseOpenApiConfigTest.java`
- `exception/BusinessExceptionTest.java`
- `exception/ClientExceptionTest.java`
- `exception/InvalidRequestExceptionTest.java`
- `exception/ResourceNotFoundExceptionTest.java`
- `exception/ServiceExceptionTest.java`
- `exception/ServiceUnavailableExceptionTest.java`

Same process as PR 1 for each file.

#### Step 2: Verify

Run `./gradlew service-web:test` — all tests must pass.

### PR 3: Documentation update

**Branch**: `chore/assertj-standardization-docs`

Can be merged alongside either PR or after both. Contains no code changes.

#### Step 1: Add assertion framework rule to testing-patterns.md

Add to the Unit Tests section (after line 157, near `**Framework**: JUnit 5 (Jupiter)`):

```
**Assertions**: AssertJ (`assertThat`) — do not use JUnit's `Assertions`
(`assertEquals`, `assertTrue`, etc.)
```

#### Step 2: Convert all code examples in testing-patterns.md

Every code snippet in the document that uses JUnit assertions must be rewritten using AssertJ.
This affects approximately 20 snippets across these sections:

- Testing Philosophy (line 60)
- Unit Tests example (line 173)
- Integration Tests example (lines 215-216)
- Mocking Strategies (line 369)
- Arrange-Act-Assert Pattern (line 457)
- Test Edge Cases (lines 617, 639-640)
- Testing Exception Handling (lines 659, 674, 685-686, 708, 711-712, 801, 828-830)
- Performance Testing (line 861)
- Common Testing Pitfalls (lines 916, 919)

#### Step 3: Add conversion reference to testing-patterns.md

Add a short section under Testing Best Practices with the JUnit-to-AssertJ conversion table from
this plan, so contributors have a quick reference if they encounter JUnit assertions in older
consumer services.

## Verification

After all three PRs are merged, confirm:

```bash
# Should return 0 files
grep -rl "import static org.junit.jupiter.api.Assertions" \
  service-core/src/test service-web/src/test

# Should return all test files that have assertions
grep -rl "import static org.assertj.core.api.Assertions" \
  service-core/src/test service-web/src/test
```
