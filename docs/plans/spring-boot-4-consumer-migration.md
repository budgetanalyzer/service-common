# Spring Boot 4 Consumer Migration Notes

## Status

Library migration complete as a verified development snapshot, `0.0.18-SNAPSHOT`, published to
Maven Local only (Phase 5). No remote publication or stable release has happened. The execution
plan is [spring-boot-4-upgrade.md](spring-boot-4-upgrade.md). This document records public API
changes, dependency changes, validation results, and consumer actions. It is input for authoring
the later consumer integration plan; it is not itself an executable plan.

`permission-service` is migrated locally, uncommitted on top of HEAD `84a1516`
([permission-service plan](spring-boot-4-permission-service-migration.md)). Against the Maven
Local snapshot it builds and passes 57 tests (the 56 existing plus one new invalid-`status`
validation case). Focused compatibility assertions cover timestamps, null omission, detail and
pagination fields, and 400/401/403/404/422/503 error shapes; they passed on Boot 3 before the
dependency change and unchanged on Boot 4. Nothing has been deployed. **Local migration verified;
deployed API compatibility pending.** The deployed gate must still run the main configuration in
Tilt (its `spring.jackson.datatype.datetime.write-dates-as-timestamps` and
`tools.jackson.databind.util.StdDateFormat` settings are shadowed in tests), confirm
`../budget-analyzer-api-tests` covers timestamps, null omission, error shapes, and the generated
OpenAPI contract against Boot 3 behavior, and record the results.

## Snapshot Line

- `0.0.18-SNAPSHOT` is the Spring Boot 4 development line. It is not a stable release.
- Nothing is published or pushed to GitHub until the entire ecosystem runs locally in Tilt and
  `budget-analyzer-api-tests` pass. Until then, consumers resolve service-common from Maven Local
  only. Keep each repository's CI definitions current as part of its migration; CI is first
  exercised at that push. Consumer plans do not sequence around publication order or CI artifact
  resolution.
- The user approved an exception to the normal backwards-compatibility, deprecation, and
  major-version rules in [versioning and compatibility](../versioning-and-compatibility.md) for
  this development snapshot only. The Jackson public API removals, logging behavior corrections,
  explicit error-response serialization policy, and framework/BOM changes below are intentional
  and ship without compatibility shims.
- A later consumer integration plan must choose the stable release version, migrate and test every
  consumer, and document rollout and rollback before a stable release.

## Consumers

Discover consumers from the published coordinates in sibling catalogs (excluding this repository):

```bash
grep -nE 'org\.budgetanalyzer:(spring-platform|spring-cloud-platform|service-core|service-web)|serviceCommon' \
  ../*/gradle/libs.versions.toml ../*/build.gradle.kts 2>/dev/null | grep -v '^\.\./service-common/'
```

At the Phase 5 library check, the four consumers pinned the published `0.0.17`. Every
service-common coordinate used `version.ref = "serviceCommon"`, and no build script declared a
literal `org.budgetanalyzer:` coordinate. The consumer HEADs at that check were
`currency-service` 919a2ee, `permission-service` 84a1516, `session-gateway` 09dacbf, and
`transaction-service` 20f84b3.

## Target Dependency Set

| Component | Before | After |
| --- | --- | --- |
| Spring Boot (BOM and plugin catalog key) | 3.5.16 | 4.1.2 |
| Spring Framework (via Boot) | 6.2.x | 7.0.10 |
| Spring Security (via Boot) | 6.5.x | 7.1.2 |
| Jackson databind (via Boot) | 2.x (`com.fasterxml.jackson`) | 3.1.7 (`tools.jackson`) |
| JUnit Jupiter (via Boot) | 5.x | 6.0.3 |
| Spring Cloud | 2025.0.3 | 2025.1.4 |
| Spring Modulith | 1.4.13 | 2.1.2 |
| SpringDoc OpenAPI | 2.9.1 | 3.1.1 |
| Testcontainers | 1.21.4 (spring-platform constraint) | 2.0.5 (Spring Boot BOM) |
| ShedLock | 6.10.0 | 7.10.1 |
| WireMock | 3.13.2 | 3.13.2 |
| Awaitility | 4.3.0 | 4.3.0 |

## Dependency Changes

### Platform artifacts

- `spring-platform` imports Spring Boot 4.1.2 and Spring Modulith 2.1.2 BOMs and constrains
  SpringDoc 3.1.1, WireMock 3.13.2, Awaitility 4.3.0, and ShedLock 7.10.1.
- `spring-cloud-platform` imports `spring-platform` and Spring Cloud 2025.1.4.
- **Platform-breaking:** the four Testcontainers constraints (`org.testcontainers:testcontainers`,
  `junit-jupiter`, `postgresql`, `rabbitmq`) and their Docker 29 pin rationale are removed. Boot
  4.1.2's imported `testcontainers-bom` manages Testcontainers 2.0.5, whose default Docker API
  (1.44) is compatible with Docker 29. The old module names `org.testcontainers:postgresql`,
  `org.testcontainers:junit-jupiter`, and `org.testcontainers:rabbitmq` have no 2.x release and are
  no longer version-managed; a consumer that still declares them fails dependency resolution.
  `org.testcontainers:testcontainers` keeps its name and is managed by the Boot BOM.

### Library modules

- `service-core`: Jackson databind is now `tools.jackson.core:jackson-databind` (still
  `implementation`); `jackson-datatype-jsr310` is removed because Jackson 3 has built-in Java time
  support. Tests use `spring-boot-starter-data-jpa-test` and `spring-boot-starter-security-test`
  instead of `spring-boot-starter-test`, `spring-security-core`, and `spring-security-test`.
- `service-web`: the servlet `compileOnly` stack is `spring-boot-starter-webmvc` (replacing the
  deprecated `spring-boot-starter-web`). Tests use the webmvc, webflux, data-jpa, and security test
  starters; `reactor-test` now arrives through `spring-boot-starter-webflux-test`.
- Main-source `api`, `implementation`, and stack/JPA `compileOnly` boundaries are unchanged, so
  consumers must still declare their own web stack and JPA starters.
- Javadoc external links target Spring Framework 7.0.10 and Jakarta EE 11.
- Springdoc 3.1.1 (through Swagger Core Jakarta 2.2.55) still brings Jackson 2 databind onto
  classpaths that include Springdoc. That is a legitimate third-party dependency; old
  `com.fasterxml.jackson.databind` imports can therefore compile but no Jackson 2 `ObjectMapper`
  bean exists at runtime, and Jackson 2 databind annotations such as `@JsonSerialize` are ignored.
  `com.fasterxml.jackson.annotation` remains valid with Jackson 3.

## Consumer Actions

Apply these during the later consumer integration plan, not from this repository. The
consolidated, consumer-assigned inventory from Phase 5 is in
[Consumer integration inventory](#consumer-integration-inventory). The per-phase "consumer
action" notes below give the reasons.

- Update the service-common coordinate (`serviceCommon`) and the consumer's own Spring Boot
  Gradle plugin version (`springBoot` catalog key, currently `3.5.16` in every consumer) together.
  A consumer on the new platform with the old Boot plugin is not a supported combination.
- Replace `spring-boot-starter-web` with `spring-boot-starter-webmvc`.
- Replace `spring-boot-starter-test` with the technology test starters the service uses
  (`spring-boot-starter-webmvc-test`, `-webflux-test`, `-data-jpa-test`, `-security-test`); each
  brings `spring-boot-starter-test`.
- Rename Testcontainers modules to `org.testcontainers:testcontainers-<module>` (for example
  `testcontainers-postgresql`, `testcontainers-junit-jupiter`, `testcontainers-rabbitmq`) and use
  the non-generic module container types such as `org.testcontainers.postgresql.PostgreSQLContainer`.
- Remove or realign explicit JUnit Platform versions so they match the JUnit 6 line managed by
  the Boot BOM.
- Migrate Jackson usage to `tools.jackson` packages. Boot 4 auto-configures a Jackson 3
  `tools.jackson.databind.json.JsonMapper` bean, and that is the supported injection target. It
  provides **no** Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper` bean. Jackson 2 classes
  can still be on the classpath through Springdoc, so Jackson 2 injection points compile and then
  fail at startup, and Jackson 2 databind annotations are silently ignored. Do not add the
  deprecated `spring-boot-jackson2` bridge or enable `spring.jackson.use-jackson2-defaults`
  wholesale.
- Spring Cloud 2025.1.x applies only to services that import `spring-cloud-platform`. Spring
  Modulith 2.1.x, SpringDoc 3.1.x, and ShedLock 7.x arrive through `spring-platform`. Follow each
  project's migration notes for consumer-owned usage.
- Business-rule 422 responses (`BusinessException`) now carry `HttpStatus.UNPROCESSABLE_CONTENT`
  instead of the deprecated `HttpStatus.UNPROCESSABLE_ENTITY`. The two are distinct enum
  constants, so code or tests that compare against `HttpStatus.UNPROCESSABLE_ENTITY` should use
  `HttpStatus.UNPROCESSABLE_CONTENT` or compare `value()`. The wire status is still 422.

## Findings From Consumer Migrations

These were found during the `permission-service` and `transaction-service` migrations and apply
to every consumer. Use them directly. A consumer plan does not need to re-derive or re-verify
them.

Dates and times:

- The Boot-managed `JsonMapper` writes `Instant` and `LocalDate` values as ISO-8601 strings
  (for example `"2026-04-08T12:30:45.123456Z"`) on both Boot 3 and Boot 4. This held in the
  permission-service and transaction-service suites, which run with Boot's default mapper
  because each service's test `application.yml` shadows the main file. Response date output
  needs no per-service investigation. The main-file `spring.jackson.*` translation (below) is
  verified at the deployed gate.
- Existing `jsonPath("$.createdAt").isNotEmpty()` assertions also pass for numeric timestamps. A
  service can cover this by adding the following next to the existing assertion. Run it on Boot 3
  before the dependency change.

  ```java
  jsonPath("$.createdAt").value(matchesRegex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z"))
  ```

  Doing so is optional.
- Standalone mappers built in tests or code behave differently. A Jackson 2 mapper built as
  `JsonMapper.builder().addModule(new JavaTimeModule()).build()` or
  `new ObjectMapper().findAndRegisterModules()` writes `Instant` values as numbers. A Jackson 3
  `JsonMapper.builder().build()` writes ISO strings. Where the exact output of such a mapper
  matters, the like-for-like translation is
  `JsonMapper.builder().enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build()`
  (`tools.jackson.databind.cfg.DateTimeFeature`). Examples are byte lengths, stored or encrypted
  payloads, and exact-string fixtures. Example: in transaction-service, the ISO payload grew
  `PreviewImportTokenServiceTest`'s ciphertext from 249 to 253 bytes. At a length of 1 mod 3, the
  last base64url character holds only ignored bits, so the test's last-character tamper stopped
  changing the token.

Build:

- Replace `flyway-core` with `spring-boot-starter-flyway`. With only `flyway-core`, no `Flyway`
  bean is created and full-context tests fail. Keep `flyway-database-postgresql`.
- Test package moves, in addition to the Gotchas list in
  [spring-boot-4-upgrade.md](spring-boot-4-upgrade.md):
  - `AutoConfigureTestDatabase` → `org.springframework.boot.jdbc.test.autoconfigure`
  - `DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure`
  - `TestEntityManager` → `org.springframework.boot.jpa.test.autoconfigure`
  - `UserDetailsServiceAutoConfiguration` → `org.springframework.boot.security.autoconfigure`

Jackson 3 API translations:

- `JsonProcessingException` is gone. Catch the unchecked `tools.jackson.core.JacksonException`.
  `readValue` no longer throws `IOException`, so an `IOException` catch around a Jackson call
  becomes `JacksonException`.
- `JsonNode.fieldNames()` is removed. Use `propertyNames()`, which returns `Collection<String>`.
- `JsonNode.textValue()` now throws on non-string and missing nodes. `stringValue(null)` keeps the
  old null result. `asText()` and `isTextual()` are deprecated but still compile and behave the
  same on string and missing nodes.
- `new ObjectMapper().findAndRegisterModules()` → `JsonMapper.builder().findAndAddModules().build()`.

Tests:

- In Spring 7, `jsonPath(path).value(nullValue())` fails when the path is absent. Null
  `ApiErrorResponse`/`FieldError` fields are now omitted, so those assertions become
  `jsonPath(path).doesNotExist()`.

Jackson 3 default changes. These were checked against 3.1.7. They are behavior changes to list in
a consumer plan, not to work around:

- `FAIL_ON_TRAILING_TOKENS` is on. A JSON request body with content after the root value returns
  400.
- `FAIL_ON_NULL_FOR_PRIMITIVES` is on. A null or missing value for a primitive request field
  returns 400. Boxed fields are unaffected.
- `SORT_PROPERTIES_ALPHABETICALLY` is on for non-record types. Records keep declaration order.
  Key order is not part of the API contract, but JSON that a service persists may change key
  order.
- `WRITE_ENUMS_USING_TO_STRING` and `READ_ENUMS_USING_TO_STRING` are on. An enum that overrides
  `toString()` changes its wire value.
- `FAIL_ON_UNKNOWN_PROPERTIES` is off, the same as Boot 3's default.

## Approved Logging And Error-Response Contract Changes

These are approved for this snapshot line. Phases 2 and 3 implement them and record the final
public API details here.

- `SafeLogger` keeps its public API and uses a private immutable logging mapper. The logging-only
  masking implementation becomes package-private; the public `SensitiveDataModule` and
  `UnknownTypeSerializer` (including `UnknownTypeSerializer.ToStringFallbackModifier`) are removed.
- The generic `toString()` fallback for empty beans is removed. Empty beans serialize as `{}` at
  root and nested positions. The three `SafeLoggerTest` generic-toString expectations change to
  empty-object expectations at the same root, map, and nested positions. Callers that need string
  output must pass known-safe string values explicitly.
- `HttpBodyLogSanitizer` keeps strict trailing-token checks: bodies with multiple JSON roots,
  including multi-record NDJSON, use the unable-to-safely-sanitize omission placeholder.
- `ApiErrorResponse` and `FieldError` declare `@JsonInclude(NON_NULL)` and an explicit property
  order, so null optional fields are omitted even for consumers whose mapper previously included
  them. Assertions that required explicit null optional error fields change to require omission.

## Phase 1: Baseline And Dependency Wiring

### Baseline (Spring Boot 3.5.16, Jackson 2)

- `./gradlew clean build --warning-mode all` passed before any change: `service-core` 184 tests,
  `service-web` 549 tests, 0 failures, 0 skipped. No Gradle deprecation warnings; JVM warnings came
  only from Netty's `sun.misc.Unsafe` use.
- Added `service-core/src/test/java/org/budgetanalyzer/core/logging/SensitiveDataSerializationTest.java`
  (14 tests) covering `SafeLogger.toJson()` with field and getter `@Sensitive` annotations, full
  masking (fixed eight-character mask), `showLast`, custom mask characters, non-string values,
  `showLast` equal to or longer than the value, null and empty values, record components, nested
  objects, objects inside root and nested lists, and objects inside maps. Tests parse the JSON,
  assert preserved ordinary fields and masked values, and assert secret portions are absent.
  Phase 2 must keep these behavioral assertions while migrating imports and parser construction.
- `./gradlew clean spotlessApply` and `./gradlew :service-core:build --warning-mode all` passed
  with the new tests on Jackson 2.

### Known defects for Phase 2

Recorded, not frozen in tests. Phase 2 adds correct-behavior tests with the redesign:

- **Array-shape masking:** the custom `BeanPropertyWriter` overrides only field serialization, so
  `@JsonFormat(shape = ARRAY)` beans serialize sensitive values unmasked.
- **Inclusion:** the writer writes the masked string itself for non-null values, bypassing
  `@JsonInclude` checks such as `NON_EMPTY` for empty sensitive strings.
- **Generic toString:** `UnknownTypeSerializer.ToStringFallbackModifier` replaces empty-bean
  serializers with `toString()`, which overrides `@JsonValue` handling and can expose private
  secrets that bypass `@Sensitive`.
- **Multi-root parsing:** `HttpBodyLogSanitizer` uses a default Jackson 2 `ObjectMapper`, which
  reads only the first JSON root, so multi-record NDJSON and trailing content log the first record
  instead of the omission placeholder.

### After the dependency switch

- Platform POMs: `spring-platform` imports `spring-boot-dependencies` 4.1.2 and
  `spring-modulith-bom` 2.1.2 and keeps the SpringDoc, WireMock, Awaitility, and ShedLock
  constraints; `spring-cloud-platform` imports `org.budgetanalyzer:spring-platform:0.0.18-SNAPSHOT`
  and `spring-cloud-dependencies` 2025.1.4. Neither contains Testcontainers constraints.
- Dependency resolution: `compileClasspath`, `testCompileClasspath`, `runtimeClasspath`, and
  `testRuntimeClasspath` for both modules contain no `FAILED` entries. Resolved Spring Boot 4.1.2,
  Spring Framework 7.0.10, Spring Security 7.1.2, Jackson databind 3.1.7, JUnit Jupiter 6.0.3,
  SpringDoc 3.1.1, `reactor-test` 3.8.8, and all four new test starters. Jackson 2.22.1 databind
  and `jackson-datatype-jsr310` remain on the `service-web` test runtime only through Springdoc's
  Swagger Core.
- As planned, main and test sources do not compile until Phases 2 and 3.

## Phase 2: Service-Core Migration

### Public API changes (`service-core`)

Absence of usage in current consumers does not make these removals backwards compatible; they are
breaking changes approved only for this snapshot line.

- **Removed** `org.budgetanalyzer.core.logging.SensitiveDataModule` (public class extending
  Jackson 2 `com.fasterxml.jackson.databind.module.SimpleModule`, with a public no-arg
  constructor). Its replacement, `SensitiveMaskingModule`, is package-private and registered only
  on `SafeLogger`'s private mapper. Consumers can no longer register `@Sensitive` masking on their
  own mappers; use `SafeLogger.toJson()` for masked log output.
- **Removed** `org.budgetanalyzer.core.logging.UnknownTypeSerializer` (public class extending
  Jackson 2 `StdSerializer<Object>`) and its public nested
  `UnknownTypeSerializer.ToStringFallbackModifier` (extending Jackson 2 `BeanSerializerModifier`).
  No replacement.
- **Changed implemented interface:** `PrometheusEndpointPostProcessor` and
  `ApplicationMetricTagPostProcessor` now implement `org.springframework.boot.EnvironmentPostProcessor`
  instead of the deprecated `org.springframework.boot.env.EnvironmentPostProcessor`; both still
  implement `Ordered`. `META-INF/spring.factories` registers them under the
  `org.springframework.boot.EnvironmentPostProcessor` key. Code that referenced either class as the
  old interface type must use the new one.
- **Unchanged:** `SafeLogger` (`toJson`, `mask`, `truncateId`) and `@Sensitive` (`maskChar`,
  `showLast`, field and method targets). `HttpBodyLogSanitizer.prepareBodyForLogging` keeps its
  signature. `ServiceCoreJpaAutoConfiguration` only relocates its `DataSourceAutoConfiguration`
  ordering reference to `org.springframework.boot.jdbc.autoconfigure`. Jackson databind remains an
  `implementation` dependency, so Jackson 3 types are not exposed on consumers' compile classpath
  through `service-core`.

### Logging behavior changes

- `SafeLogger.toJson()` uses a private immutable Jackson 3 `JsonMapper` (indented output,
  `FAIL_ON_EMPTY_BEANS` disabled, `SORT_PROPERTIES_ALPHABETICALLY` disabled so properties keep
  declaration order). Java time values use Jackson 3's built-in ISO-8601 strings; `JavaTimeModule`
  is gone. Null roots still produce `null`, and empty maps still produce `{ }`.
- `@Sensitive` masking is applied by an annotation introspector inserted ahead of Jackson's
  default introspector. It replaces only the value, so it now:
  - masks values in `@JsonFormat(shape = ARRAY)` beans (previously leaked unmasked);
  - respects `@JsonInclude(NON_EMPTY)` for empty sensitive strings (previously written as `""`);
  - takes precedence over another serializer, such as `@JsonSerialize(using = ...)`, declared on
    the same member;
  - leaves `null` handling and property naming to Jackson.
  Mask length, custom mask characters, `showLast`, and short, empty, and non-string value handling
  are unchanged.
- The generic `toString()` fallback is removed. Types without serializable properties serialize as
  `{}` at root, map, and nested positions, and `toString()` is never invoked, so private secrets it
  might expose stay out of logs. Known Jackson serializers, including `@JsonValue`, are preserved
  (the old fallback overrode them). **Consumer action:** callers that relied on string output for
  such types must pass an explicitly known-safe value, for example `httpMethod.name()`. Both HTTP
  logging filters in `service-web` already normalize methods to strings.
- `HttpBodyLogSanitizer` uses a private `JsonMapper.builder().build()` with Jackson 3's default
  strict trailing-token check. Bodies with more than one JSON root (multi-record NDJSON, trailing
  garbage) now produce `[structured body omitted: <content type>, unable to safely sanitize]`
  instead of logging the sanitized first record. A single JSON root followed by `\n` or `\r\n`
  is still sanitized and logged. Truncated and invalid structured bodies are still omitted.

### Test-only changes

- `SensitiveDataSerializationTest` migrated to a Jackson 3 `JsonMapper` parser and `asString()` /
  `isString()` accessors; its 14 baseline assertions are unchanged. Added regression tests for
  `NON_EMPTY` omission, array-shaped beans, precedence over a property serializer, preserved
  `@JsonValue` output, and that a plain mapper does not apply logging-only masking.
- `SafeLoggerTest`: the three generic-toString tests now expect empty objects at the same root,
  map, and nested positions, keeping their ordinary-field assertions. Added tests for exact ISO
  date output, an empty bean whose `toString()` would expose a private `@Sensitive` field (asserts
  `{}`, secret absent, `toString()` not invoked), declaration-order output, and indentation.
- `HttpBodyLogSanitizerTest`: added multi-record NDJSON and trailing-garbage omission tests (secrets
  absent) and single-record NDJSON tests ending in `\n` and `\r\n` that are sanitized.
- `TestApplication` imports `EntityScan` from `org.springframework.boot.persistence.autoconfigure`.
- Hibernate 7 logged `HHH90000025` (H2Dialect does not need to be specified explicitly), so the
  explicit H2 dialect was removed from `src/test/resources/application.yml` and
  `src/test/resources/META-INF/persistence.xml`.
- No build file change was needed: the data-jpa starter already exposes `spring-boot-jdbc` for
  the relocated `DataSourceAutoConfiguration` reference.

### Validation

- `./gradlew clean spotlessApply` passed.
- `./gradlew :service-core:test --tests 'org.budgetanalyzer.core.logging.*'` passed (99 tests).
  Temporarily enabling `SORT_PROPERTIES_ALPHABETICALLY` made the ordering test fail, confirming
  it detects the setting; the change was reverted.
- `./gradlew :service-core:build --warning-mode all` passed: 212 tests, 0 failures, 0 skipped,
  including JPA auditing and soft-delete tests. Checkstyle, Spotless, and Javadoc passed. JaCoCo:
  89.3% line, 78.4% branch (minimums 80% and 70%). No Gradle deprecation or Hibernate
  `HHH9xxxx` deprecation warnings remained.
- `rg -n 'com\.fasterxml\.jackson\.(core|databind|datatype)' service-core/src` returned exit code 1
  (no matches). `com.fasterxml.jackson.annotation` imports remain valid and are used in tests.
- `service-web` is intentionally not yet migrated and does not compile (Phase 3).

## Phase 3: Service-Web Migration

### Public API changes (`service-web`)

Breaking signature changes approved only for this snapshot line. Absence of usage in current
consumers does not make them backwards compatible.

- **Changed constructor parameter:** `ReactiveErrorWebExceptionHandler(ObjectMapper)` is now
  `ReactiveErrorWebExceptionHandler(tools.jackson.databind.json.JsonMapper)`. It still implements
  Boot's `ErrorWebExceptionHandler`, now `org.springframework.boot.webflux.error.ErrorWebExceptionHandler`
  (relocated from `org.springframework.boot.web.reactive.error`), and `Ordered`.
- **Changed bean method parameter:** `ServiceWebAutoConfiguration.errorWebExceptionHandler(ObjectMapper)`
  is now `errorWebExceptionHandler(JsonMapper)`. Its `@ConditionalOnMissingBean` target and its
  ordering against `ErrorWebFluxAutoConfiguration` (now
  `org.springframework.boot.webflux.autoconfigure.error`) are unchanged.
- **Changed constructor parameter:** `ClaimsHeaderAuthenticationFilter(ObjectMapper)` is now
  `ClaimsHeaderAuthenticationFilter(JsonMapper)`.
- **Changed bean method parameters:** `ClaimsHeaderSecurityConfig.securityFilterChain(HttpSecurity, ObjectMapper)`
  is now `securityFilterChain(HttpSecurity, JsonMapper)`, and
  `ReactiveClaimsHeaderSecurityConfig.securityWebFilterChain(ServerHttpSecurity, ObjectMapper)` is
  now `securityWebFilterChain(ServerHttpSecurity, JsonMapper)`. Both inject the Boot-managed
  `JsonMapper` bean; a context with only a Jackson 2 `ObjectMapper` bean no longer starts them.
- **Order constant source:** the servlet chain keeps order `BASIC_AUTH_ORDER`, now read from
  `org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties`. Boot 4 moved
  that constant off `SecurityProperties`, which remains at
  `org.springframework.boot.security.autoconfigure` without it. The order value is unchanged.
- **Nullness annotations:** `CorrelationIdFilter` and `HttpLoggingFilter` override parameters use
  `org.jspecify.annotations.NonNull` instead of the deprecated `org.springframework.lang.NonNull`.
  JSpecify arrives through Spring Framework 7.
- **Unchanged:** `ApiErrorResponse` and `FieldError` construction APIs, field values, and getters;
  `BaseOpenApiConfig` public and protected signatures; and the published test support in
  `org.budgetanalyzer.service.security.test` (`ClaimsHeaderTestBuilder`, `TestClaimsSecurityConfig`).
  These compile unchanged against Spring Security Test 7 and Spring Test 7.

### Error-response serialization policy

- `ApiErrorResponse` declares `@JsonInclude(NON_NULL)` and `@JsonPropertyOrder({"type", "message",
  "code", "fieldErrors"})`; `FieldError` declares `@JsonInclude(NON_NULL)` and
  `@JsonPropertyOrder({"index", "field", "message", "rejectedValue"})`. Both use
  `com.fasterxml.jackson.annotation`.
- **Behavior change:** null `code`, `fieldErrors`, `index`, and `rejectedValue` are omitted on
  every path that serializes through a Jackson mapper, even when the application mapper includes
  nulls (`spring.jackson.default-property-inclusion=always`). A null `rejectedValue` is now
  indistinguishable from an absent one. Empty lists and empty strings are still written.
  **Consumer action:** clients and tests that require `"code": null` or similar must accept
  omission. Compare parsed structure and values instead of raw JSON text.
- Direct writers still serialize through the injected Boot-managed `JsonMapper`, so application
  mapper customization (for example indentation) still applies.
- Failure handling: `ReactiveErrorWebExceptionHandler` catches Jackson 3's unchecked
  `JacksonException` and returns `Mono.error`, leaving status and headers unset as before.
  `ReactiveClaimsHeaderSecurityErrorResponseWriter` catches `JacksonException` and writes the
  existing minimal `INTERNAL_ERROR` JSON fallback. The servlet writers gain no handling: they had no
  serialization fallback, and the servlet `PrintWriter` records I/O errors instead of throwing.
- Spring Framework 7 deprecates `HttpStatus.UNPROCESSABLE_ENTITY` in favor of a distinct constant,
  `UNPROCESSABLE_CONTENT` (both 422), and `HttpStatus.valueOf(422)` now returns
  `UNPROCESSABLE_CONTENT`. The `BusinessException` handlers return `UNPROCESSABLE_CONTENT`; the
  wire status is still 422. `BaseOpenApiConfig.buildExampleApiErrorResponse` maps both constants
  to `APPLICATION_ERROR`. Framework-originated 422
  statuses (for example `ResponseStatusException(422)`) now default to the reason phrase
  `Unprocessable Content`. **Consumer action:** code that compares `HttpStatus` enum identity with
  `UNPROCESSABLE_ENTITY` should compare `value()` or handle both constants.

### Test-only changes

- Relocated test imports: `EntityScan` (`org.springframework.boot.persistence.autoconfigure`),
  `ErrorWebExceptionHandler`, `JacksonAutoConfiguration`, `SecurityAutoConfiguration`,
  `DispatcherServletAutoConfiguration`, `WebMvcAutoConfiguration`, `AutoConfigureMockMvc`, and
  `AutoConfigureWebTestClient` (see the plan's Gotchas for packages).
- Jackson 2 parsing and `withBean(ObjectMapper.class, ObjectMapper::new)` became Jackson 3
  `JsonMapper` equivalents. Assertions are unchanged apart from the `asText()` to `asString()`
  accessor rename.
- `ServletApiExceptionHandlerTest` uses Spring 7's three-argument
  `NoResourceFoundException(HttpMethod, String requestPath, String resourcePath)` constructor. The
  two-argument constructor was removed.
- The explicit Hibernate H2 dialect was removed from `src/test/resources/application.yml` and
  `src/test/resources/META-INF/persistence.xml` (Hibernate 7 `HHH90000025`).
  `spring.main.allow-bean-definition-overriding` was kept.
- Added coverage:
  - `ApiErrorResponseTest` and `FieldErrorTest`: populated optional fields in explicit order,
    null-field omission, and kept empty lists/strings. Each runs against a plain `JsonMapper`,
    Boot's auto-configured `JsonMapper`, and Boot's mapper with
    `default-property-inclusion=always`, using the shared fixture `ErrorModelJsonMappers`.
  - `ReactiveErrorWebExceptionHandlerTest`: serialization-failure propagation, using a real mapper
    with the concrete `FailingApiErrorResponseSerializer`.
  - New `ReactiveClaimsHeaderSecurityErrorResponseWriterTest`: 401 and 403 bodies, and the 500
    fallback on serialization failure.
  - `BaseOpenApiConfigTest`: both 422 constants map to `APPLICATION_ERROR`.
  Temporarily removing the model annotations, or the 422 mapping, made the corresponding tests
  fail; each change was restored.
- No existing HTTP error-path test asserted explicit null optional fields, so none changed for the
  omission policy.

### Build changes

- `service-web/build.gradle.kts` (from Phase 1) needed no further change: the modular starters
  already supply every relocated type. Stack, JPA, security, Springdoc, and security-test
  dependencies stay `compileOnly`.

### Consumer test migration actions

- Import Boot test annotations and autoconfiguration classes from their Boot 4 modules (Gotchas
  list), and declare `@AutoConfigureMockMvc` / `@AutoConfigureWebTestClient` explicitly.
- Inject or construct `tools.jackson.databind.json.JsonMapper` in tests. Replace
  `withBean(ObjectMapper.class, ...)` with `withBean(JsonMapper.class, ...)`, and rename
  `JsonNode.asText()` to `asString()`.
- Pass a `JsonMapper` wherever a test constructs `ClaimsHeaderAuthenticationFilter` or
  `ReactiveErrorWebExceptionHandler` directly.
- Replace assertions that require null optional error fields with omission assertions.
- Use Spring 7's three-argument `NoResourceFoundException` constructor.

### Validation

- `./gradlew clean spotlessApply` passed.
- Focused run, `./gradlew :service-web:test --tests '*Security*' --tests '*ExceptionHandler*'
  --tests '*ConfigTest' --tests '*ResponseTest' --tests '*FieldErrorTest'`, passed: 274 tests, 0
  failures.
- `rg -n 'com\.fasterxml\.jackson\.(core|databind|datatype)' service-web/src` exited 1 (no
  matches).
- `./gradlew clean build --warning-mode all` passed. `service-core`: 212 tests, 89.3% line, 78.4%
  branch. `service-web`: 573 tests, 0 failures, 0 skipped, 95.3% line, 82.6% branch (minimums 93%
  and 80%, unchanged). Spotless, Checkstyle, and Javadoc passed with no Gradle deprecation
  warnings.
- Remaining test-log warnings are standard Boot, Springdoc, and JPA defaults plus Netty's JVM native-access notice; none involve
  Jackson or bean wiring.

## Phase 4: Consumer Classpaths And Runtime Behavior

### Regression found and fixed

- **Servlet-only classpaths failed to start `ServiceWebAutoConfiguration`.** Boot 4 moved
  `ErrorWebExceptionHandler` out of the always-present `spring-boot` jar into the WebFlux-only
  `spring-boot-webflux` module. The reactive fallback handler bean method was declared on the outer
  `ServiceWebAutoConfiguration` class, and its return type `ReactiveErrorWebExceptionHandler`
  implements that interface. A servlet consumer without WebFlux therefore could not introspect the
  outer auto-configuration. The combined-classpath suite could not detect this because WebFlux is
  always present there.
- **Public API change (approved snapshot exception):** the public bean method
  `errorWebExceptionHandler(JsonMapper)` moved from `ServiceWebAutoConfiguration` to its nested
  `ServiceWebAutoConfiguration.ReactiveWebConfiguration` class. The bean name
  (`errorWebExceptionHandler`), type, `@ConditionalOnMissingBean(ErrorWebExceptionHandler.class)`
  back-off, and reactive-only activation are unchanged. The outer class keeps its ordering before
  Boot's `ErrorWebFluxAutoConfiguration`, now declared by name (`beforeName`) so the class
  reference does not require WebFlux. **Consumer action:** none unless code calls the bean method
  on `ServiceWebAutoConfiguration` directly; such code must use the nested configuration or
  construct `ReactiveErrorWebExceptionHandler` itself.

### Test and build changes

- `service-core`: `ServiceCoreClasspathTest` uses `ApplicationContextRunner` with a
  `FilteredClassLoader` that hides servlet, reactive, Netty, JPA/Hibernate, and Spring Security
  classes. It loads the registered service-core auto-configurations through `ImportCandidates`.
  The test confirms both are registered, the context starts, the CSV parser bean works, and
  `SafeLogger` and `HttpBodyLogSanitizer` mask secrets. It also confirms that
  `ServiceCoreJpaAutoConfiguration`, `AuditorAware`, and JPA auditing are absent.
- `service-web`: new JVM test suites, `servletSmokeTest` and `reactiveSmokeTest`, are wired into
  `check`. Each depends on `project()` (the library's published API and runtime dependencies) plus
  only its own stack starter and stack test starter, Spring Security and its test starter, and the
  matching SpringDoc UI starter (`webmvc-ui` / `webflux-ui`). Neither inherits the combined
  `testRuntimeClasspath`, adds JPA, or declares a mapper bean. The fixture applications are plain
  `@SpringBootApplication` classes in `org.budgetanalyzer.smoke.*`, so library beans arrive only
  through auto-configuration discovery.
- Root build: `jacocoTestReport` now finalizes only each module's `test` task instead of every
  `Test` task. Coverage reports and the unchanged minimums still cover the `test` task only; the
  smoke suites supplement them.
- Two existing MockMvc assertions moved from the deprecated `status().isUnprocessableEntity()` to
  `status().isUnprocessableContent()`. Both check status value 422, so the assertions are
  equivalent.
- Gradle suite registration uses `register<JvmTestSuite>(name)`; the `by registering` delegate
  syntax is deprecated for Gradle 10.

### Runtime behavior verified (both stacks, real HTTP to a random-port embedded server)

- Resolved runtime graphs: `servletSmokeTestRuntimeClasspath` contains `spring-webmvc`, Tomcat, and
  `springdoc-openapi-starter-webmvc-api`, with no `spring-webflux`, `reactor-netty-http`,
  WebFlux SpringDoc, or JPA/Hibernate. `reactiveSmokeTestRuntimeClasspath` contains
  `spring-webflux`, Reactor Netty, and `springdoc-openapi-starter-webflux-api`, with no
  `spring-webmvc`, Tomcat, servlet API, MVC SpringDoc, or JPA/Hibernate. Each startup test also
  asserts the context type (`ServletWebServerApplicationContext` /
  `ReactiveWebServerApplicationContext`) and the absence of the other stack's classes.
- Success response through Boot's default `JsonMapper`. That mapper still writes `null`
  properties, which shows that error-model omission is not global `non_null` configuration.
- 401 without claims (`type`, `message` only); 403 from `@PreAuthorize` with insufficient
  permissions; 404 and 422 responses that omit absent optional fields, with 422 including `code`;
  and 400 validation responses with `fieldErrors` that omit absent `index` / `rejectedValue`.
- Consumer-defined chains: on servlet, a lower-order consumer `SecurityFilterChain` handles its
  matched requests while the shared chain still protects others. On reactive, a consumer
  `SecurityWebFilterChain` replaces the shared chain (the shared bean is absent), and reactive
  `@PreAuthorize` still returns a 403 `FORBIDDEN` `ApiErrorResponse`.
- `/actuator/health/liveness`, `/actuator/health/readiness`, and `/actuator/prometheus` are
  reachable without claims. The fixtures configure neither actuator exposure nor metric tags, so
  Prometheus exposure and the `application="<spring.application.name>"` tag on
  `jvm_memory_used_bytes` come from the `spring.factories`-discovered post-processors.
- `/v3/api-docs` with a fixture extending `BaseOpenApiConfig` documents the following. GET
  operations get generated 500/503 responses that reference `ApiErrorResponse` and use the
  matching example type. An explicit 404 description is preserved. The `@PreAuthorize` POST gets
  400/401/403/500/503, and an unsecured operation gets no 401/403. The `ApiErrorResponse` and
  `FieldError` schemas are present.
- No behavior differences beyond the approved contract changes were found.

### Validation

- `./gradlew clean spotlessApply` passed.
- `./gradlew :service-core:test --tests '*Classpath*'` passed (5 tests).
- `./gradlew :service-web:servletSmokeTest :service-web:reactiveSmokeTest` passed (13 servlet
  and 13 reactive tests).
- `./gradlew clean build --warning-mode all` passed, running both smoke tasks through `check`.
  `service-core`: 217 tests, 89.3% line, 78.9% branch (minimums 80% / 70%). `service-web`: 573
  tests, 95.3% line, 82.6% branch (minimums 93% / 80%). Smoke suites: 26 tests. No failures or
  skips; no Gradle deprecation warnings.
- `service-web` `compileJava` and `compileTestJava` emit no javac deprecation notes. Netty's JVM
  native-access notice and the JVM CDS notice are environment warnings, not repository-source
  deprecations.

## Phase 5: Documentation Review And Local Publication

### Documentation review

- `AGENTS.md`, `README.md`, and every `docs/*.md` file were scanned for the deprecated
  `spring-boot-starter-web`, standalone `spring-boot-starter-test`, Jackson 2
  `ObjectMapper`/`com.fasterxml.jackson.{core,databind,datatype}`/`JavaTimeModule`, old
  Testcontainers coordinates and generic `PostgreSQLContainer<?>`,
  `org.testcontainers.containers`, relocated Boot packages, the removed logging classes, and
  `@MockBean`/`@SpyBean`/`@MockitoBean`/`@MockitoSpyBean`. The only remaining matches are the
  `docs/testing-patterns.md` prohibition on mocking or spying application-owned beans (kept) and
  the technology test-starter description in `docs/common-patterns.md`. `GenericContainer<?>`
  stays generic in Testcontainers 2. `spring.jackson.default-property-inclusion`, referenced by
  `docs/spring-boot-conventions.md`, still exists in Boot 4.1.2.
- `docs/advanced-patterns.md` (Transactional Outbox configuration): the `event_publication`
  example now includes the nullable `status`, `completion_attempts`, and
  `last_resubmission_date` columns that Spring Modulith 2 maps. A version-agnostic note says to
  match the managed Modulith schema and add columns through Flyway before upgrading when
  `ddl-auto: validate` is used. The example's `delete-completion-after: 30d` was replaced with
  `completion-mode: update`. `delete-completion-after` is not a Spring Modulith property and is
  ignored. `completion-mode` (`update`, `delete`, `archive`) is the real setting, and `update` is
  the default, which keeps completed publications.
- `AGENTS.md` Testing Patterns discovery now lists the `servletSmokeTest`/`reactiveSmokeTest`
  sources and run command. Instructions and guardrails are otherwise unchanged. The README
  documents the smoke suites (Phase 4).
- Canonical docs remain version-agnostic. Release-specific detail lives only in this document.

### Consumer integration inventory

Observed read-only from the consumers' checked-in sources on 2026-10-23. Re-verify it when the
integration plan is authored.

| Area | `currency-service` | `permission-service` | `session-gateway` | `transaction-service` |
| --- | --- | --- | --- | --- |
| Platform | `spring-cloud-platform` | `spring-platform` | `spring-platform` | `spring-platform` |
| Stack starter | `-web` (+ `-webflux` dependency) | `-web` | `-webflux` | `-web` |
| SpringDoc UI starter | webmvc | webmvc | webflux | webmvc |
| Old Testcontainers modules | `junit-jupiter`, `postgresql`, `rabbitmq` | `junit-jupiter`, `postgresql` | `junit-jupiter` | `junit-jupiter`, `postgresql` |
| `spring-boot-testcontainers` | yes | no | yes | no |
| Spring Cloud | Cloud Stream + Rabbit binder | no | no | no |
| Spring Modulith | starter-core, starter-jpa, starter-test | no | no | no |
| ShedLock | `shedlock-spring`, JDBC provider | no | no | no |
| Redis | `data-redis` + `cache`, `GenericJackson2JsonRedisSerializer` | `data-redis` | `ReactiveStringRedisTemplate` | no |

Required for every consumer:

- Update `serviceCommon` and `springBoot` (the Boot Gradle plugin, currently `3.5.16`) together.
- Replace `spring-boot-starter-test` with technology test starters. Rename the old Testcontainers
  modules above to their `testcontainers-` artifacts and update container imports and types.
- Each consumer pins `junitPlatform = "1.14.4"` for `junit-platform-launcher`. That is the JUnit 5
  platform line, and Boot 4.1.2 manages JUnit 6.0.3. Remove the explicit version so the BOM
  manages the launcher, or realign it.
- Remove the deprecated `status().isUnprocessableEntity()` uses, or plan to remove them:
  `currency-service` 7, `permission-service` 1, `transaction-service` 21. These are deprecation
  warnings, not failures, and the status value is still 422.
- Review `SafeLogger.toJson()` call sites (`currency-service`: `FredClient`,
  `CurrencyServiceStartupConfig`, `ExchangeRateImportScheduler`; `transaction-service`:
  `TransactionServiceStartupConfig`). Confirm that the logged types have serializable properties.
  Types without properties now log `{}` instead of their `toString()`.
- No consumer references the removed or changed service-common APIs (`SensitiveDataModule`,
  `UnknownTypeSerializer`, `ReactiveErrorWebExceptionHandler`/`ClaimsHeaderAuthenticationFilter`
  constructors, `ServiceWebAutoConfiguration` bean methods, or the environment post-processors).
  The breaking changes recorded above still apply to any new usage.

Jackson code (Jackson 2 databind/core/datatype imports; injecting an `ObjectMapper` bean fails at
startup):

- `currency-service` main: `FredClient` and `CacheConfig`. Test: `FredApiStubs`.
- `session-gateway` main: `SecurityConfig` injects `ObjectMapper` and will not start until it
  uses `JsonMapper`.
- `transaction-service` main: `PdfStatementFormatWizardService`, `PreviewImportTokenService`,
  `StatementFormatService`, and `StatementExtractorRegistry`. Tests: seven integration and unit test
  classes.
- `permission-service` test: `PermissionOpenApiIntegrationTest`.

`spring.jackson.*` configuration (checked against the Boot 4.1.2 configuration metadata and
Jackson 3.1.7):

- `currency-service`, `permission-service`, and `transaction-service` set
  `default-property-inclusion: non_null`, `serialization.indent-output: true`,
  `serialization.write-dates-as-timestamps: false`, and
  `date-format: com.fasterxml.jackson.databind.util.StdDateFormat`. `session-gateway` sets only
  `default-property-inclusion: non_null`.
- `default-property-inclusion` and `serialization.indent-output` still exist.
- `WRITE_DATES_AS_TIMESTAMPS` is no longer a Jackson 3 `SerializationFeature`. It is a
  `tools.jackson.databind.cfg.DateTimeFeature`, which Boot binds under
  `spring.jackson.datatype.datetime.*`. The `spring.jackson.serialization` map is keyed by Jackson
  3 `SerializationFeature`, so the current key no longer names a valid feature. Jackson 3 already
  writes dates as ISO strings by default.
- `spring.jackson.date-format` still exists, but `StdDateFormat` moved to
  `tools.jackson.databind.util.StdDateFormat`. The Jackson 2 class can still resolve from
  Springdoc's transitive Jackson 2, so a stale value may not fail visibly.
- Integration plan requirements: translate keys like-for-like. Move
  `serialization.write-dates-as-timestamps` to `datatype.datetime.write-dates-as-timestamps`, and
  change the `date-format` value to `tools.jackson.databind.util.StdDateFormat`. Do not remove
  settings and do not restore all Jackson 2 defaults. The existing service tests and the later
  api-tests run are the verification; this change does not call for new tests.

Redis, cache, and persisted serialization (validate separately from API JSON):

- `currency-service` `CacheConfig` builds a `GenericJackson2JsonRedisSerializer` from an injected
  Jackson 2 `ObjectMapper` with `activateDefaultTyping(NON_FINAL)`. It needs a Jackson 3 based
  serializer and polymorphic type validator from the managed Spring Data Redis line. Verify
  class names during migration. The plan must test cache round-trips for every cached type and
  decide how to handle entries written in the old format, for example by evicting them or
  changing the key prefix, before rollout.
- `session-gateway` stores string values through `ReactiveStringRedisTemplate`. Verify that any
  JSON it writes and reads across a rolling deployment keeps the same shape under Jackson 3.
  `permission-service` declares `spring-boot-starter-data-redis` in its catalog only; it does not
  use Redis, so there is nothing to migrate.
- `currency-service` Spring Modulith event publications: the Flyway `V5` `event_publication`
  table has only the legacy six columns, and the service uses `ddl-auto: validate`. Add the
  nullable Modulith 2 columns (`status`, `completion_attempts`, `last_resubmission_date`) in a
  new expand-only migration before the upgrade. Its
  `spring.modulith.events.delete-completion-after: 30d` has no effect. Decide `completion-mode`
  and retention explicitly. Outstanding publications were serialized with Jackson 2, so verify
  that Modulith 2 can deserialize incomplete rows, or drain them before deployment.

Cloud, ShedLock, SpringDoc:

- Spring Cloud 2025.1.4 (Oakwood) applies only to `currency-service`. That service is the only
  `spring-cloud-platform` consumer and uses Cloud Stream with the Rabbit binder. Verify its
  function bindings and Rabbit binder configuration against the Oakwood notes. `session-gateway`
  uses `spring-platform` and has no Cloud migration.
- ShedLock 7.10.1 applies to `currency-service` (`shedlock-spring`, JDBC template provider).
  Follow the ShedLock 7 notes and verify that scheduled locks still acquire against the existing
  `shedlock` table.
- SpringDoc 3.1.1 applies to all four consumers through the platform constraint. The consumer
  catalogs declare SpringDoc without versions. Verify each service's generated `/v3/api-docs`
  against its OpenAPI tests.

### Outstanding gates (not satisfied by this iteration)

- No migrated consumer has been built, tested, started, or deployed against `0.0.18-SNAPSHOT`.
  Local publication makes the artifacts resolvable from Maven Local only.
- The later consumer integration plan must choose the stable release version, migrate and test
  all four consumers (including the Redis, cache, Modulith, and date-output checks above), and
  document rollout and rollback before a stable release.
- Remote publication, git operations, and the GitHub Actions major updates from the dashboard
  remain out of scope.

### Local publication

- Maven Local: no `MAVEN_OPTS`, `maven.repo.local`, or `~/.m2/settings.xml` override, so the
  default `~/.m2/repository` is used. `./gradlew publishToMavenLocal` ran after the build
  passed and wrote all four artifacts under
  `org/budgetanalyzer/<artifact>/0.0.18-SNAPSHOT/`.
- `spring-platform`: POM and `.module` only (no jar). The POM imports
  `spring-boot-dependencies:4.1.2` and `spring-modulith-bom:2.1.2` and manages
  `springdoc-openapi-starter-webmvc-ui`/`-webflux-ui` 3.1.1, `wiremock-standalone` 3.13.2,
  `awaitility` 4.3.0, and `shedlock-spring`/`shedlock-provider-jdbc-template` 7.10.1. It has no
  Testcontainers constraint. Module metadata matches: both BOMs as platform dependencies plus
  the same six constraints.
- `spring-cloud-platform`: POM and `.module` only. It imports
  `org.budgetanalyzer:spring-platform:0.0.18-SNAPSHOT` and `spring-cloud-dependencies:2025.1.4`,
  and the module metadata matches.
- `service-core`: jar, `-sources.jar`, `-javadoc.jar`, POM, and `.module`. The POM imports
  `spring-platform:0.0.18-SNAPSHOT`. Compile scope: `spring-boot-starter-actuator` 4.1.2 and
  `micrometer-registry-prometheus` 1.17.2. Runtime scope: `tools.jackson.core:jackson-databind`
  3.1.7, `slf4j-api`, and `opencsv`. Compared with the published `0.0.17`, only the Jackson
  coordinate changed and `jackson-datatype-jsr310` is gone. No JPA, Hibernate, web stack, or
  security dependency is published. The module `apiElements` variant excludes Jackson.
- `service-web`: jar, `-sources.jar`, `-javadoc.jar`, POM, and `.module`. The POM imports
  `spring-platform:0.0.18-SNAPSHOT`, and its compile scope holds `service-core:0.0.18-SNAPSHOT`,
  `commons-lang3`, and `spring-boot-starter-validation`. That is the same dependency set as
  `0.0.17`. WebMVC, WebFlux, JPA, Security, and SpringDoc remain unpublished `compileOnly`
  boundaries, and the smoke-suite dependencies are not published.
- Registration resources: `service-core` jar
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` lists
  `ServiceCoreAutoConfiguration` and `ServiceCoreJpaAutoConfiguration`. Its
  `META-INF/spring.factories` registers `PrometheusEndpointPostProcessor` and
  `ApplicationMetricTagPostProcessor` under `org.springframework.boot.EnvironmentPostProcessor`.
  The `service-web` jar imports `ServiceWebAutoConfiguration`, `ClaimsHeaderSecurityConfig`, and
  `ReactiveClaimsHeaderSecurityConfig`. Both imports files are byte-identical to source.
- Neither binary nor sources jar contains `SensitiveDataModule`, `UnknownTypeSerializer`, or
  smoke-fixture (`org.budgetanalyzer.smoke`) classes. `service-core` contains the package-private
  `SensitiveMaskingModule`, and `service-web` still ships the
  `org.budgetanalyzer.service.security.test` support classes.

### Validation

- Consumer discovery (`rg -l` over sibling `gradle/libs.versions.toml`) found four consumers.
  `rg -n '^serviceCommon\s*='` showed `"0.0.17"` in each, and `git show HEAD:` confirmed the same
  committed pins with clean catalog and build files.
- Documentation changed only after Phase 4, so its full-build evidence still applied. The build
  was rerun anyway for fresh evidence: `./gradlew clean build --warning-mode all` passed.
  `service-core` 217 tests, 89.3% line, 78.9% branch. `service-web` 573 tests, 95.3% line, 82.6%
  branch. `servletSmokeTest` 13 and `reactiveSmokeTest` 13 tests ran through `check`. 0 failures,
  0 errors, 0 skipped. Coverage minimums are unchanged.
- `./gradlew publishToMavenLocal` passed.
- `rg -n 'com\.fasterxml\.jackson\.(core|databind|datatype)' service-*/src` exited 1 (no
  matches).
