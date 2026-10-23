# Spring Boot 4 Permission-Service Migration Plan

Migrate `permission-service` from Spring Boot 3.5.16 / service-common `0.0.17` to Spring Boot
4.1.2 / service-common `0.0.18-SNAPSHOT` with the smallest change that preserves API behavior.
Background and the mechanical item list are in
[spring-boot-4-permission-service-research.md](spring-boot-4-permission-service-research.md).
General consumer caveats are in [spring-boot-4-consumer-migration.md](spring-boot-4-consumer-migration.md).
This plan governs implementation and verification scope where those background notes differ.

Plan-wide constraints:

- Preserve endpoints, HTTP methods, parameters and validation, status codes, relevant response
  headers, response fields and values (including timestamp representation and null omission).
  Preserve authentication/authorization, filtering, sorting, pagination, and mutation/retry
  effects. JSON key order, whitespace, and OpenAPI formatting are not part of the contract.
- Reuse existing tests; add focused assertions or cases where needed to establish API
  compatibility against Boot 3 behavior. Never weaken or delete existing assertions. Do not
  introduce a new verification framework, snapshot system, or general-purpose harness.
- Any observable API regression blocks acceptance. Fix it at the owning layer; do not accept
  changed expectations merely because the new framework behaves differently.
- Translate configuration like-for-like (renamed keys and classes only). Do not remove or redesign
  settings, including the unused `spring.data.redis` properties, the explicit Hibernate dialects,
  and `src/main/resources/application-test.yml`.
- Do not read orchestration or api-tests internals.
- No git write operations. Leave all changes uncommitted.

Pre-plan evidence (2026-10-23): a throwaway copy of `permission-service` at HEAD `84a1516` was
migrated with the mechanical changes listed in Phase 1. `./gradlew clean spotlessApply` and
`./gradlew clean build --warning-mode all` passed. The run had 56 tests with 0 failures, errors, or
skips, the same count as the unmodified Boot 3 baseline. JaCoCo, Checkstyle, and Spotless gates
passed. Observed results:

- `spring-boot-starter-flyway` is required. With only `flyway-core`, the `Flyway` bean is missing
  and the full-context integration tests fail.
- The generated `/v3/api-docs` is byte-identical before and after. SpringDoc 3 does not
  deduplicate the `status` enum, so the research doc's possible OpenAPI difference does not occur.
- Every relocated class and the `spring.jackson.datatype.datetime` key were confirmed against the
  Boot 4.1.2 and Jackson 3.1.7 jars.
- No service-common defect blocks the suite, so there is no service-common fix phase.

Compatibility evidence and remaining verification:

- No contract change was observed in the dry run; this does not establish full API parity.
  Null omission is expected to remain unchanged: service-common `0.0.18` always
  omits null optional `ApiErrorResponse`/`FieldError` fields, and this service already used
  `default-property-inclusion: non_null`. Business-rule responses keep wire status 422, although
  the Java constant is now `HttpStatus.UNPROCESSABLE_CONTENT`. The service has no JSON request
  bodies, so Jackson 3's stricter deserialization defaults have no input to act on.
- The test `application.yml` shadows the main one. Both files translate the timestamp setting,
  but only the main file sets `tools.jackson.databind.util.StdDateFormat`. Confirming these keys
  and classes exist does not verify the main configuration at runtime. That remains part of the
  deployed API gate below.

Publication: nothing is pushed or published to GitHub until the whole ecosystem runs locally in
Tilt and api-tests pass (see the Snapshot Line in
[spring-boot-4-consumer-migration.md](spring-boot-4-consumer-migration.md#snapshot-line)). Keep CI
definitions current, but do not plan around publication order or CI artifact resolution.

Deployed API gate: during the existing ecosystem verification stage, run permission-service with
its main configuration in Tilt and run `../budget-analyzer-api-tests`. Confirm the suite covers
representative timestamp values, null omission, error-response shapes, and the behavior listed
above; close any gaps with focused checks in the existing suite. Compare with Boot 3 behavior,
including the generated OpenAPI contract. Record the results before accepting API compatibility
or publishing. This later ecosystem work is outside these two local phases. Until it passes,
report: **Local migration verified; deployed API compatibility pending.**

## Phase 1: Migrate Permission-Service To Boot 4

### Workspace

../permission-service

### Goal

Bring `permission-service` onto Spring Boot 4.1.2 and service-common `0.0.18-SNAPSHOT` so that
`./gradlew clean build` passes with all existing tests and focused compatibility assertions.
Keep production changes mechanical unless a demonstrated API regression requires a minimal fix.

### Scope

- `gradle/libs.versions.toml` and `build.gradle.kts`
- `src/main/java/org/budgetanalyzer/permission/PermissionServiceApplication.java`
- `src/main/resources/application.yml` and `src/test/resources/application.yml`
- Test sources: `PermissionServiceIntegrationTestSupport`, `UserRoleRepositoryIntegrationTest`,
  `SeedDataIntegrationTest`, `UserIdentityIntegrationTest`, `UserControllerIntegrationTest`,
  `InternalPermissionControllerIntegrationTest`, and `PermissionOpenApiIntegrationTest`
- Minimal compatibility fixes in permission-service and their affected documentation, only if
  verification demonstrates a regression. Report the evidence and any resulting scope extension.
- Review only, no change expected: `.github/workflows/*.yml` and `Dockerfile`. Pre-plan review
  found nothing Boot-version-specific: JDK 25, `./gradlew build`, the
  `build/test-results/test/*.xml` upload path, and the `bootJar` → `build/libs/*.jar` copy are
  unchanged.

### Non-goals

- New verification infrastructure, broad test rewrites, and unrelated refactoring. A separate
  local `bootRun` check is not required; the main configuration is verified at the deployed gate.
- Removing deprecation warnings that do not block the build, for example Hibernate 7's
  `HHH90000025` explicit-dialect notice. Keep both `database-platform` settings.
- Dropping the redundant `spring-security-test` catalog entry and dependency, or the unused
  `spring-boot-starter-data-redis` catalog entry and `spring.data.redis` properties.
- Edits to `application-test.yml`. It has no Jackson keys.
- README or `docs/` changes for mechanical renames alone; update affected documentation if a
  compatibility fix changes configuration or operating guidance.
- Any change in service-common, orchestration, or api-tests.

### Required context

- `../service-common/docs/plans/spring-boot-4-permission-service-research.md` (Configuration,
  Build Changes, Source Changes)
- `../service-common/docs/code-quality-standards.md` (required by this repository's AGENTS.md
  before modifying Java)
- `../service-common/docs/testing-patterns.md` before modifying tests
- The files listed in Scope

### Execution steps

1. Prerequisite: confirm `0.0.18-SNAPSHOT` of `service-web`, `service-core`, and `spring-platform`
   exists in Maven Local (`~/.m2/repository/org/budgetanalyzer/<artifact>/0.0.18-SNAPSHOT/`), and
   that Docker is available. If either is missing, stop and report; do not publish service-common
   from this workspace.
   Before changing dependencies, extend the existing controller tests with focused assertions
   for representative serialized timestamps and complete error-response shapes, including null
   omission. Use controlled timestamps in existing fixtures and assert Boot 3 values; do not
   freeze generated IDs or clock-dependent values. Run the affected tests on Boot 3 first, then
   retain the same expectations on Boot 4. Reuse existing behavioral coverage for permissions,
   pagination, and deactivation/retry semantics; add cases only for concrete uncovered risks.
2. `gradle/libs.versions.toml`:
   - Set `springBoot = "4.1.2"` and `serviceCommon = "0.0.18-SNAPSHOT"`.
   - Delete the `junitPlatform` version and drop `version.ref = "junitPlatform"` from
     `junit-platform-launcher`, so the Boot BOM manages it.
   - Replace `spring-boot-starter-web` with
     `spring-boot-starter-webmvc = { module = "org.springframework.boot:spring-boot-starter-webmvc" }`.
   - Replace `spring-boot-starter-test` with three entries: `spring-boot-starter-webmvc-test`,
     `spring-boot-starter-data-jpa-test`, and `spring-boot-starter-security-test`, all in group
     `org.springframework.boot`.
   - Replace `flyway-core` with
     `spring-boot-starter-flyway = { module = "org.springframework.boot:spring-boot-starter-flyway" }`.
     Keep `flyway-database-postgresql`.
   - Change the `testcontainers-junit-jupiter` and `testcontainers-postgresql` modules to
     `org.testcontainers:testcontainers-junit-jupiter` and
     `org.testcontainers:testcontainers-postgresql`. Aliases stay unchanged.
3. `build.gradle.kts`:
   - Change `implementation(libs.spring.boot.starter.web)` to `libs.spring.boot.starter.webmvc`.
   - Change `implementation(libs.flyway.core)` to `libs.spring.boot.starter.flyway`.
   - Replace `testImplementation(libs.spring.boot.starter.test)` with `testImplementation` of
     `libs.spring.boot.starter.webmvc.test`, `libs.spring.boot.starter.data.jpa.test`, and
     `libs.spring.boot.starter.security.test`.
   - Change nothing else.
4. Configuration:
   - In both `application.yml` files, move `spring.jackson.serialization.write-dates-as-timestamps:
     false` to `spring.jackson.datatype.datetime.write-dates-as-timestamps: false`. Keep
     `serialization.indent-output` and the main file's inline comment.
   - In `src/main/resources/application.yml` only, change `date-format` to
     `tools.jackson.databind.util.StdDateFormat`.
5. Main source: in `PermissionServiceApplication`, import `UserDetailsServiceAutoConfiguration`
   from `org.springframework.boot.security.autoconfigure`.
6. Test source migration (retain the compatibility assertions established in step 1):
   - `AutoConfigureTestDatabase` →
     `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`
   - `DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`
   - `TestEntityManager` → `org.springframework.boot.jpa.test.autoconfigure.TestEntityManager`
   - `AutoConfigureMockMvc` →
     `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`. This applies to
     the three controller and OpenAPI tests.
   - `org.testcontainers.containers.PostgreSQLContainer<?>` / `new PostgreSQLContainer<>(...)` →
     the non-generic `org.testcontainers.postgresql.PostgreSQLContainer` /
     `new PostgreSQLContainer(...)`, in the four container files. `Container` and `Testcontainers`
     imports in `org.testcontainers.junit.jupiter` are unchanged.
   - `PermissionOpenApiIntegrationTest`:
     - `com.fasterxml.jackson.databind.JsonNode` → `tools.jackson.databind.JsonNode`.
     - Replace the injected `ObjectMapper objectMapper` with
       `tools.jackson.databind.json.JsonMapper jsonMapper` and update `readTree`.
     - Rename each `asText()` to `asString()`.
   - `InternalPermissionControllerIntegrationTest`: `status().isUnprocessableEntity()` →
     `status().isUnprocessableContent()`. Both assert 422.
7. Confirm that `.github/workflows/*.yml` and `Dockerfile` reference nothing the steps above
   changed: task names, JDK, test-result paths, and jar output. Update a reference only if a
   change here invalidated it.
8. Run `./gradlew clean spotlessApply`, then `./gradlew clean build --warning-mode all`.
9. Fix mechanical migration failures and demonstrated API regressions owned by this repository.
   If a failure traces to service-common behavior, stop and report the failing test, expected
   Boot 3 behavior, and actual result (including a stack trace when relevant). Acceptance remains
   blocked until the shared library is fixed in its own workspace; do not work around it here.

### Implementation notes

- A pre-plan dry run passed with the mechanical changes; the added compatibility assertions
  still need verification on both versions. Let
  `spotlessApply` place the `tools.jackson` imports; no manual ordering is needed.
- Every Boot 4 starter, Testcontainers 2 module, and the JUnit launcher is version-managed through
  `spring-platform`. Add no versions.
- `PermissionServiceSecurityConfig`, `SessionGatewayClient`/`PermissionServiceConfig`
  (self-built `RestClient`), `UserSpecifications`, the repositories, and all other main sources
  compile unchanged. Leave them alone unless a demonstrated compatibility regression requires
  a minimal fix.

### Validation

- `./gradlew clean spotlessApply` succeeds.
- Focused compatibility assertions pass on Boot 3 and Boot 4 with unchanged expectations.
- `./gradlew clean build --warning-mode all` succeeds: all 56 existing tests plus any focused new
  cases, 0 failures, errors, or skips
  (sum `tests`, `failures`, `errors`, and `skipped` across `build/test-results/test/*.xml`).
  JaCoCo, Checkstyle, and Spotless gates pass.
- `rg -n 'com\.fasterxml\.jackson\.(core|databind|datatype)|org\.testcontainers\.containers\.PostgreSQLContainer|org\.springframework\.boot\.test\.autoconfigure\.(orm|jdbc|web)|org\.springframework\.boot\.autoconfigure\.security|isUnprocessableEntity|junitPlatform|starter-web"|flyway-core|"org\.testcontainers:(junit-jupiter|postgresql)"' src gradle build.gradle.kts`
  returns no matches.
- `git status --short` lists only the files in Scope.

### Completion criteria

- The worktree has the Scope changes uncommitted, and nothing else.
- The full build passes with every existing test retained and unchanged coverage gates.
- Existing assertions are preserved apart from equivalent API renames; new compatibility
  assertions have passed against both Boot versions.
- Final report includes the test count, compatibility checks, any scope extensions, and
  deprecation warnings. State: "Local migration verified; deployed API compatibility pending."

## Phase 2: Record Permission-Service Migration Status In Service-Common Docs

### Workspace

.

### Goal

Update service-common's Boot 4 migration notes so they reflect that permission-service now builds
and passes its suite against `0.0.18-SNAPSHOT`. Record observed results and explicitly retain the
outstanding deployed API gate.

### Scope

- `docs/plans/spring-boot-4-consumer-migration.md`
- `docs/plans/spring-boot-4-permission-service-research.md`

### Non-goals

- Editing canonical (version-agnostic) docs, `AGENTS.md`, `README.md`, or any source or build file.
- Recording anything for the other consumers.

### Required context

- The two files in Scope.
- Phase 1 results: `git -C ../permission-service status --short` and
  `git -C ../permission-service log -1 --format=%h` (read-only), plus the test count from
  `../permission-service/build/test-results/test/*.xml`, and the Phase 1 compatibility results.

### Execution steps

1. In `spring-boot-4-consumer-migration.md`, `## Status`, add one or two sentences saying that
   `permission-service` has been migrated locally, uncommitted on top of the recorded HEAD. It
   builds and passes its suite (state the test count and focused compatibility checks) against
   the Maven Local snapshot, and nothing has been deployed. State: "Local migration verified;
   deployed API compatibility pending." Record the main-configuration and API coverage checks
   still required by the deployed gate.
2. In `spring-boot-4-permission-service-research.md`, make these replacements, keeping the doc a
   research note:
   - Replace the *unverified* Flyway note with the observed result: `spring-boot-starter-flyway`
     is required, and without it the `Flyway` bean is missing.
   - Attribute the byte-identical OpenAPI and unchanged duplicated enum to the pre-plan dry run;
     do not imply that Phase 1 repeated a full OpenAPI comparison.
   - Replace the stale-Jackson-key note with the translated key and observed test results;
     verification of the main configuration remains pending.
   - Replace the blanket ban on new tests with the focused compatibility approach used here.
3. Keep wording concise and consistent with the surrounding sections.

### Implementation notes

- These are release-specific notes under `docs/plans/`, so version-specific detail belongs here,
  not in canonical docs.
- Read only `../permission-service`. Do not read orchestration or api-tests.

### Validation

- `git status --short` shows only the two Scope files (plus this plan, if it is still
  uncommitted).
- `rg -n -i 'unverified' docs/plans/spring-boot-4-permission-service-research.md` returns only
  items that are still open, if any remain.
- Every changed sentence is consistent with the Phase 1 results.

### Completion criteria

- Both docs distinguish verified local results from pending deployed API compatibility.
