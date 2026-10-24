# Spring Boot 4 Transaction-Service Migration Plan

Migrate `transaction-service` from Spring Boot 3.5.16 / service-common `0.0.17` to Spring Boot
4.1.2 / service-common `0.0.18-SNAPSHOT`. Make only the changes required to compile, start, and
pass the existing transaction-service test suite. The request said "permission-service test
suite"; this plan reads that as transaction-service. General consumer caveats are in
[spring-boot-4-consumer-migration.md](spring-boot-4-consumer-migration.md). The
[permission-service plan](spring-boot-4-permission-service-migration.md) is the worked example for
the build and test relocations. This plan governs scope where those documents differ. In
particular, ignore the consumer notes' "context-startup test" and "assert date output"
suggestions.

Plan-wide constraints:

- Contract means the same endpoints, parameters, status codes, response fields, and values. JSON
  key order, whitespace, and OpenAPI formatting are not part of the contract.
- Do not add tests, snapshots, smoke checks, or any verification harness. The existing tests are
  the local verification. `../budget-analyzer-api-tests` is the verification after all services
  are migrated and deployed. You may augment an existing test to cover fragile behavior; this plan
  adds one such assertion, for `Instant` output. Never weaken or delete existing assertions.
- Translate configuration like-for-like (renamed keys and classes only). Do not remove or redesign
  settings.
- If the migration forces a behavior change, list it here. Do not build anything to detect or
  work around it.
- Do not read orchestration or api-tests internals.
- No git write operations. Leave all changes uncommitted.

Pre-plan evidence (2026-10-23): two throwaway copies of `transaction-service` were made at HEAD
`20f84b3`.

- The unmodified Boot 3 baseline ran 477 tests with 0 failures, errors, or skips.
- The other copy had every change in Phase 1 applied. `./gradlew clean spotlessApply` and
  `./gradlew clean build --warning-mode all` passed: 477 tests, 0 failures, errors, or skips, with
  JaCoCo, Checkstyle, and Spotless green.
- The only compiler note was a deprecation note from test sources (`isUnprocessableEntity`,
  `asText`). It is left as is.
- The `Instant` assertion added in step 1 passed on both copies.
- No service-common defect blocks the suite, so there is no service-common fix phase.

One existing test fails unless its fixture is translated carefully:
`PreviewImportTokenServiceTest.verifyTokenTamperedCiphertextThrowsInvalidToken`. The fixture's
standalone mapper (Jackson 2 with `JavaTimeModule`, Jackson 2 defaults) wrote `Instant` values as
numeric timestamps, giving a 249-byte ciphertext. A default Jackson 3 mapper writes ISO strings,
giving a 253-byte ciphertext. At a length of 1 mod 3, the last base64url character carries only
two data bits. Swapping `x`/`y` there changes only ignored bits, so the "tampered" token decodes
to the same bytes and verifies. This was confirmed with `java.util.Base64`. The like-for-like fix
is to enable `DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS` on the fixture mapper, which restores
the fixture's original payload. The test logic stays the same. Production is not affected: its
tokens come from the Boot mapper, which writes ISO dates on both Boot 3 and Boot 4, so the token
format and length are unchanged.

Behavior changes forced by the migration (listed only; nothing is built to detect them):

- Jackson 3 enables `FAIL_ON_TRAILING_TOKENS`. A JSON request body with trailing content after the
  root value now returns 400 instead of being accepted.
- Jackson 3 enables `FAIL_ON_NULL_FOR_PRIMITIVES`. Request records use boxed types, so nothing on
  the wire changes.
- Jackson 3 sorts properties alphabetically for non-record types; records keep declaration order.
  Key order is not part of the contract. Newly written `parser_config` JSON and preview-token
  payloads may differ in key order and whitespace. Existing rows (including the `V18` seed rows
  with explicit nulls) and in-flight tokens read back the same, since all their fields are
  `String`, `Integer`, enums, or ISO `Instant`.
- Business-rule responses keep wire status 422. The Java constant is now
  `HttpStatus.UNPROCESSABLE_CONTENT`.
- service-common `0.0.18` always omits null `ApiErrorResponse`/`FieldError` fields. Production
  already used `default-property-inclusion: non_null`, so production responses do not change. The
  test context uses Boot defaults (nulls included), so seven existing `fieldErrors` null assertions
  become absence assertions.

Main configuration: `src/test/resources/application.yml` fully shadows the main file, so the
translated `spring.jackson.*` keys in `src/main/resources/application.yml` are not exercised
locally. Every key and class was confirmed to exist in Boot 4.1.2 and Jackson 3.1.7. Runtime
verification is part of the deployed gate below.

Publication: nothing is pushed or published to GitHub until the whole ecosystem runs locally in
Tilt and api-tests pass. See the Snapshot Line in
[spring-boot-4-consumer-migration.md](spring-boot-4-consumer-migration.md#snapshot-line).

Deployed API gate: after all services are migrated, run transaction-service with its main
configuration in Tilt and run `../budget-analyzer-api-tests`. This later ecosystem work is outside
these two local phases. Until it passes, report: **Local migration verified; deployed API
compatibility pending.**

## Phase 1: Migrate Transaction-Service To Boot 4

### Workspace

../transaction-service

### Goal

Bring `transaction-service` onto Spring Boot 4.1.2 and service-common `0.0.18-SNAPSHOT`.
`./gradlew clean build` must pass with all 477 existing tests. Production changes stay mechanical:
renamed dependencies, configuration keys, imports, and Jackson types.

### Scope

- `gradle/libs.versions.toml` and `build.gradle.kts`
- `src/main/resources/application.yml`
- Main sources: `PdfStatementFormatWizardService`, `PreviewImportTokenService`,
  `StatementFormatService`, `extractor/StatementExtractorRegistry`
- Test sources:
  - `TransactionServiceApplicationIntegrationTest`
  - `api/ControllerIntegrationTestSupport`
  - `api/SavedViewControllerAuthorizationIntegrationTest`
  - `api/TransactionControllerIntegrationTest`
  - `api/TransactionOpenApiIntegrationTest`
  - `repository/SavedViewSchemaMigrationIntegrationTest`
  - `repository/SavedViewTransactionRepositoryIntegrationTest`
  - `repository/StatementFormatRepositoryIntegrationTest`
  - `repository/TransactionRepositoryIntegrationTest`
  - `repository/spec/TransactionSpecificationsIntegrationTest`
  - `service/PdfStatementFormatWizardServiceTest`
  - `service/PreviewImportTokenServiceTest`
  - `service/SavedViewServiceIntegrationTest`
  - `service/StatementFormatServiceIntegrationTest`
  - `service/TransactionImportServiceIntegrationTest`
  - `service/TransactionServiceIntegrationTest`
  - `service/extractor/StatementExtractorRegistryIntegrationTest`
- `README.md` Technology Stack lines (`Spring Boot 3.x`, `JUnit 5`)
- Review only, no change expected:
  - `.github/workflows/*.yml` and `Dockerfile`. Pre-plan review found JDK 25, `./gradlew build`,
    `**/build/test-results/test/*.xml`, and `build/libs/*.jar`; nothing Boot-specific.
  - `docs/configuration.md`, which has no Jackson keys.
  - `src/test/resources/application.yml`, which has no Jackson keys.

### Non-goals

- New tests, snapshot or OpenAPI comparison files, a local `bootRun` check, or a
  context-startup test.
- Removing deprecation warnings that do not block the build. Leave the 21
  `status().isUnprocessableEntity()` calls, the `JsonNode.asText()` calls, and `isTextual()` as
  they are.
- Dropping the redundant `spring-security-test` dependency, or any other cleanup or refactoring.
- Any change to `TransactionServiceStartupConfig`. Its `SafeLogger.toJson()` call logs
  `HttpLoggingProperties`, which has serializable properties.
- Any change in service-common, orchestration, or api-tests.

### Required context

- `../service-common/docs/code-quality-standards.md` (required by this repository's AGENTS.md
  before modifying Java).
- `../service-common/docs/testing-patterns.md` before modifying tests.
- `../service-common/docs/plans/spring-boot-4-permission-service-migration.md` Phase 1 steps 2–6,
  as a worked example of the same catalog, build, and test relocations.
- The files listed in Scope.

### Execution steps

1. Prerequisites and the Boot 3 assertion:
   - Confirm that `0.0.18-SNAPSHOT` of `service-web`, `service-core`, and `spring-platform` is in
     Maven Local (`~/.m2/repository/org/budgetanalyzer/<artifact>/0.0.18-SNAPSHOT/`), and that
     Docker is available. If either is missing, stop and report. Do not publish service-common
     from this workspace.
   - Before changing dependencies, add the `Instant` assertion to
     `TransactionControllerIntegrationTest`:
     - Add the constant
       `private static final String ISO_INSTANT_PATTERN = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";`.
     - Add a static import of `org.hamcrest.Matchers.matchesRegex`.
     - In `createsMinimalManualTransactionWithCanonicalLocationAndNullProvenance`, keep the existing
       `isNotEmpty()` lines. After each of them add
       `.andExpect(jsonPath("$.createdAt").value(matchesRegex(ISO_INSTANT_PATTERN)))`, and the
       same for `$.updatedAt`.
     - Run `./gradlew test --tests '*TransactionControllerIntegrationTest'` on Boot 3 and confirm
       it passes before continuing.
2. `gradle/libs.versions.toml`:
   - Set `springBoot = "4.1.2"` and `serviceCommon = "0.0.18-SNAPSHOT"`.
   - Delete the `junitPlatform` version and drop `version.ref = "junitPlatform"` from
     `junit-platform-launcher`.
   - Replace `spring-boot-starter-web` with
     `spring-boot-starter-webmvc = { module = "org.springframework.boot:spring-boot-starter-webmvc" }`.
   - Replace `spring-boot-starter-test` with `spring-boot-starter-webmvc-test`,
     `spring-boot-starter-data-jpa-test`, and `spring-boot-starter-security-test`, all in group
     `org.springframework.boot`. Keep `spring-security-test`.
   - Replace `flyway-core` with
     `spring-boot-starter-flyway = { module = "org.springframework.boot:spring-boot-starter-flyway" }`.
     Keep `flyway-database-postgresql`.
   - Change the `testcontainers-postgresql` and `testcontainers-junit-jupiter` modules to
     `org.testcontainers:testcontainers-postgresql` and
     `org.testcontainers:testcontainers-junit-jupiter`. The aliases stay the same.
3. `build.gradle.kts`:
   - Change `implementation(libs.spring.boot.starter.web)` to `libs.spring.boot.starter.webmvc`.
   - Change `implementation(libs.flyway.core)` to `libs.spring.boot.starter.flyway`.
   - Replace `testImplementation(libs.spring.boot.starter.test)` with `testImplementation` of
     `libs.spring.boot.starter.webmvc.test`, `libs.spring.boot.starter.data.jpa.test`, and
     `libs.spring.boot.starter.security.test`.
   - Change nothing else.
4. `src/main/resources/application.yml`:
   - Move `spring.jackson.serialization.write-dates-as-timestamps: false` to
     `spring.jackson.datatype.datetime.write-dates-as-timestamps: false`, keeping its inline
     comment.
   - Change `date-format` to `tools.jackson.databind.util.StdDateFormat`.
   - Keep `default-property-inclusion` and `serialization.indent-output` unchanged.
5. Main sources. Make these changes in `PdfStatementFormatWizardService`,
   `PreviewImportTokenService`, `StatementFormatService`, and `StatementExtractorRegistry`:
   - Replace `com.fasterxml.jackson.databind.ObjectMapper` with
     `tools.jackson.databind.json.JsonMapper`. The field, constructor parameter, and Javadoc
     `@param` become `jsonMapper`.
   - Replace `com.fasterxml.jackson.core.JsonProcessingException` with
     `tools.jackson.core.JacksonException`. Catch variables become `jacksonException` where they
     were named `jsonProcessingException`.
   - In `StatementExtractorRegistry`, the CSV path's `catch (Exception ...)` stays as is.
   - In `PreviewImportTokenService`, `encode` catches `GeneralSecurityException | JacksonException`
     and `decode` catches `GeneralSecurityException | JacksonException`, replacing `IOException`.
     Remove the unused `java.io.IOException` import.
6. Test-source relocations:
   - `org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase` →
     `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`
   - `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` →
     `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`
   - `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` →
     `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, in
     `ControllerIntegrationTestSupport`
   - `org.testcontainers.containers.PostgreSQLContainer<?>` / `new PostgreSQLContainer<>(...)` →
     the non-generic `org.testcontainers.postgresql.PostgreSQLContainer` /
     `new PostgreSQLContainer(...)`. The `org.testcontainers.junit.jupiter` imports stay the same.
7. Jackson test changes:
   - In `SavedViewControllerAuthorizationIntegrationTest`, `TransactionControllerIntegrationTest`,
     `TransactionOpenApiIntegrationTest`, `StatementFormatServiceIntegrationTest`, and
     `StatementExtractorRegistryIntegrationTest`, replace `@Autowired private ObjectMapper
     objectMapper` with `@Autowired private JsonMapper jsonMapper` (`tools.jackson.databind.json`)
     and update the uses.
   - `SavedViewControllerAuthorizationIntegrationTest`: change all seven
     `jsonPath("$.fieldErrors").value(nullValue())` to `jsonPath("$.fieldErrors").doesNotExist()`
     and remove the unused `nullValue` import. In Spring 7, `value(...)` fails on a missing path,
     and service-common `0.0.18` omits null `fieldErrors`.
   - `TransactionOpenApiIntegrationTest`:
     - Import `tools.jackson.databind.JsonNode`.
     - Change `schemaJsonNode.path("$ref").textValue()` to `.stringValue(null)`. The Jackson 3
       `textValue()` throws on non-string nodes; `stringValue(null)` keeps the old null result.
     - Change the `fieldNames()`-based `propertyNames` helper body to
       `return List.copyOf(jsonNode.propertyNames());`. Keep `StreamSupport`, which other helpers
       still use.
   - `PdfStatementFormatWizardServiceTest`: change `new ObjectMapper().findAndRegisterModules()`
     to `JsonMapper.builder().findAndAddModules().build()`.
   - `PreviewImportTokenServiceTest`:
     - Replace the `OBJECT_MAPPER` fixture with
       `private static final JsonMapper JSON_MAPPER = JsonMapper.builder().enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();`,
       importing `tools.jackson.databind.cfg.DateTimeFeature`. This matches the old fixture's
       numeric `Instant` output (see the preamble).
     - Change `JsonProcessingException.class` to `JacksonException.class`.
     - Remove the `ObjectMapper`, Jackson 2 `JsonMapper`, `JavaTimeModule`, and
       `JsonProcessingException` imports.
8. `README.md` Technology Stack: change `Spring Boot 3.x` to `Spring Boot 4.x` and `JUnit 5` to
   `JUnit 6`.
9. Confirm that `.github/workflows/*.yml`, `Dockerfile`, and `docs/configuration.md` reference
   nothing the steps above changed. Update a reference only if a change here invalidated it.
10. Run `./gradlew clean spotlessApply`, then `./gradlew clean build --warning-mode all`.
11. Fix mechanical migration failures in this repository. If a failure traces to service-common
    behavior, stop and report the failing test, the Boot 3 behavior, and the actual result,
    including a stack trace when relevant. Do not work around it here; the service-common fix
    needs its own phase.

### Implementation notes

- The pre-plan dry run applied exactly these changes and passed. Let `spotlessApply` order the
  `tools.jackson` imports and wrap long lines.
- Every Boot 4 starter, Testcontainers 2 module, and the JUnit launcher is version-managed through
  `spring-platform`. Add no versions.
- Boot 4 registers only a Jackson 3 `JsonMapper` bean. Jackson 2 still arrives through SpringDoc,
  so a stray `com.fasterxml` `ObjectMapper` injection compiles but fails at startup. The
  validation `rg` catches this.
- `com.fasterxml.jackson.annotation.JsonIgnore` in `UpdateSavedViewTransactionsRequest` is still
  correct; Jackson 3 keeps the 2.x annotations package. Leave it.
- Jackson 3 `JsonNode.asText()` and `isTextual()` are deprecated but behave the same on the nodes
  these tests read. Leave them.
- All other main sources, including `TransactionRepository` (`SoftDeleteOperations`), the security
  configuration, and the specifications, compile and pass unchanged.

### Validation

- `./gradlew test --tests '*TransactionControllerIntegrationTest'` passes on Boot 3 with the
  step 1 assertion, before the dependency change.
- `./gradlew clean spotlessApply` succeeds.
- `./gradlew clean build --warning-mode all` succeeds with 477 tests and 0 failures, errors, or
  skips. Sum `tests`, `failures`, `errors`, and `skipped` across
  `build/test-results/test/*.xml`. JaCoCo, Checkstyle, and Spotless gates pass.
- This command returns no matches:
  `rg -n 'com\.fasterxml\.jackson\.(core|databind|datatype)|org\.testcontainers\.containers\.PostgreSQLContainer|org\.springframework\.boot\.test\.autoconfigure\.(orm|jdbc|web)|junitPlatform|starter-web"|flyway-core|"org\.testcontainers:(junit-jupiter|postgresql)"|value\(nullValue\(\)\)' src gradle build.gradle.kts`
- `git status --short` lists only files in Scope.

### Completion criteria

- The worktree has the Scope changes uncommitted, and nothing else.
- The full build passes with all 477 existing tests. Existing assertions are preserved apart from
  the equivalent API translations listed above.
- The final report includes the test count, the deprecation notes left in place, and the
  behavior-change list from the preamble. It states: "Local migration verified; deployed API
  compatibility pending."

## Phase 2: Record Transaction-Service Migration Status In Service-Common Docs

### Workspace

.

### Goal

Update the service-common Boot 4 consumer notes to show that transaction-service builds and
passes its suite against `0.0.18-SNAPSHOT`. Keep the deployed API gate marked as outstanding.

### Scope

- `docs/plans/spring-boot-4-consumer-migration.md`

### Non-goals

- Editing canonical (version-agnostic) docs, `AGENTS.md`, `README.md`, or any source or build file.
- Recording anything for other consumers.

### Required context

- The file in Scope (`## Status`).
- Phase 1 results, read-only:
  - `git -C ../transaction-service status --short`
  - `git -C ../transaction-service log -1 --format=%h`
  - The test count from `../transaction-service/build/test-results/test/*.xml`

### Execution steps

1. In `## Status`, after the permission-service paragraph, add a short paragraph:
   - `transaction-service` is migrated locally, uncommitted on top of the recorded HEAD
     ([transaction-service plan](spring-boot-4-transaction-service-migration.md)).
   - Against the Maven Local snapshot, it builds and passes its suite (state the test count),
     including the added ISO-`Instant` assertion that passed on Boot 3 first.
   - Nothing has been deployed. State: "Local migration verified; deployed API compatibility
     pending."
   - The main-configuration Jackson keys are shadowed in tests and remain part of the deployed
     gate.
2. Keep the wording concise and consistent with the surrounding sections.

### Implementation notes

- This is a release-specific note under `docs/plans/`, so version-specific detail belongs here.
- Read only `../transaction-service`. Do not read orchestration or api-tests.

### Validation

- `git status --short` shows only the Scope file, plus this plan if it is still uncommitted.
- Every changed sentence is consistent with the Phase 1 results.

### Completion criteria

- The consumer notes separate the verified local transaction-service result from the pending
  deployed API compatibility.
