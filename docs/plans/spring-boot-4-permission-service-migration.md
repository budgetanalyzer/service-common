# Spring Boot 4 Permission-Service Migration Plan

Migrate `permission-service` from Spring Boot 3.5.16 / service-common `0.0.17` to Spring Boot
4.1.2 / service-common `0.0.18-SNAPSHOT` with the minimal changeset that compiles, starts, and
passes the existing test suite. Background and the mechanical item list are in
[spring-boot-4-permission-service-research.md](spring-boot-4-permission-service-research.md).
General consumer caveats are in [spring-boot-4-consumer-migration.md](spring-boot-4-consumer-migration.md).
Ignore that document's "context-startup test" and "assert date output" suggestions.

Plan-wide constraints:

- Contract means the same endpoints, parameters, status codes, response fields and values. JSON
  key order, whitespace, and OpenAPI formatting are not part of it.
- Do not add tests, snapshots, smoke checks, or any verification harness. Do not modify, weaken, or
  delete existing tests beyond the mechanical import, type, and accessor renames listed below. The
  existing suite now, and `../budget-analyzer-api-tests` after all services are migrated and
  deployed, are the verification.
- Translate configuration like-for-like (renamed keys and classes only). Do not remove or redesign
  settings, including the unused `spring.data.redis` properties, the explicit Hibernate dialects,
  and `src/main/resources/application-test.yml`.
- Do not read orchestration or api-tests internals.
- No git write operations. Leave all changes uncommitted.

Pre-plan evidence (2026-10-23): a throwaway copy of `permission-service` at HEAD `84a1516` was
migrated with exactly the Phase 1 changes. `./gradlew clean spotlessApply` and
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

Migration-forced behavior changes (listed only; build nothing to detect or work around them):

- None in the contract was observed. Error bodies are unchanged: service-common `0.0.18` always
  omits null optional `ApiErrorResponse`/`FieldError` fields, and this service already used
  `default-property-inclusion: non_null`. Business-rule responses keep wire status 422, although
  the Java constant is now `HttpStatus.UNPROCESSABLE_CONTENT`. The service has no JSON request
  bodies, so Jackson 3's stricter deserialization defaults have no input to act on.
- The test `application.yml` shadows the main one, so the main file's translated Jackson settings
  (`datatype.datetime.write-dates-as-timestamps`, `tools.jackson.databind.util.StdDateFormat`) run
  only in a deployed service. The keys and class were confirmed against Boot 4.1.2 metadata;
  api-tests covers them after deployment.

Publication: nothing is pushed or published to GitHub until the whole ecosystem runs locally in
Tilt and api-tests pass (see the Snapshot Line in
[spring-boot-4-consumer-migration.md](spring-boot-4-consumer-migration.md#snapshot-line)). Keep CI
definitions current, but do not plan around publication order or CI artifact resolution.

## Phase 1: Migrate Permission-Service To Boot 4

### Workspace

../permission-service

### Goal

Bring `permission-service` onto Spring Boot 4.1.2 and service-common `0.0.18-SNAPSHOT` so that
`./gradlew clean build` passes with the existing 56 tests, using only mechanical build,
configuration, import, type, and accessor changes.

### Scope

- `gradle/libs.versions.toml` and `build.gradle.kts`
- `src/main/java/org/budgetanalyzer/permission/PermissionServiceApplication.java`
- `src/main/resources/application.yml` and `src/test/resources/application.yml`
- Test sources: `PermissionServiceIntegrationTestSupport`, `UserRoleRepositoryIntegrationTest`,
  `SeedDataIntegrationTest`, `UserIdentityIntegrationTest`, `UserControllerIntegrationTest`,
  `InternalPermissionControllerIntegrationTest`, and `PermissionOpenApiIntegrationTest`
- Review only, no change expected: `.github/workflows/*.yml` and `Dockerfile`. Pre-plan review
  found nothing Boot-version-specific: JDK 25, `./gradlew build`, the
  `build/test-results/test/*.xml` upload path, and the `bootJar` → `build/libs/*.jar` copy are
  unchanged.

### Non-goals

- New tests, assertions, snapshots, smoke or startup checks, or a `bootRun` check.
- Removing deprecation warnings that do not block the build, for example Hibernate 7's
  `HHH90000025` explicit-dialect notice. Keep both `database-platform` settings.
- Dropping the redundant `spring-security-test` catalog entry and dependency, or the unused
  `spring-boot-starter-data-redis` catalog entry and `spring.data.redis` properties.
- Edits to `application-test.yml`. It has no Jackson keys.
- README or `docs/` changes. They name no affected versions, artifacts, or keys.
- Any change in service-common, orchestration, or api-tests.

### Required context

- `../service-common/docs/plans/spring-boot-4-permission-service-research.md` (Configuration,
  Build Changes, Source Changes)
- `../service-common/docs/code-quality-standards.md` (required by this repository's AGENTS.md
  before modifying Java)
- The files listed in Scope

### Execution steps

1. Prerequisite: confirm `0.0.18-SNAPSHOT` of `service-web`, `service-core`, and `spring-platform`
   exists in Maven Local (`~/.m2/repository/org/budgetanalyzer/<artifact>/0.0.18-SNAPSHOT/`), and
   that Docker is available. If either is missing, stop and report; do not publish service-common
   from this workspace.
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
6. Test sources (import, type, and accessor renames only):
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
9. If the build fails, fix only mechanical migration causes in this repository. If a failure
   traces to service-common behavior, stop and report it with the failing test and stack trace.
   Do not work around it here.

### Implementation notes

- A pre-plan dry run applied exactly these steps and passed with no further edits. Let
  `spotlessApply` place the `tools.jackson` imports; no manual ordering is needed.
- Every Boot 4 starter, Testcontainers 2 module, and the JUnit launcher is version-managed through
  `spring-platform`. Add no versions.
- `PermissionServiceSecurityConfig`, `SessionGatewayClient`/`PermissionServiceConfig`
  (self-built `RestClient`), `UserSpecifications`, the repositories, and all other main sources
  compile unchanged. Do not touch them.

### Validation

- `./gradlew clean spotlessApply` succeeds.
- `./gradlew clean build --warning-mode all` succeeds: 56 tests, 0 failures, errors, or skips
  (sum `tests`, `failures`, `errors`, and `skipped` across `build/test-results/test/*.xml`).
  JaCoCo, Checkstyle, and Spotless gates pass.
- `rg -n 'com\.fasterxml\.jackson\.(core|databind|datatype)|org\.testcontainers\.containers\.PostgreSQLContainer|org\.springframework\.boot\.test\.autoconfigure\.(orm|jdbc|web)|org\.springframework\.boot\.autoconfigure\.security|isUnprocessableEntity|junitPlatform|starter-web"|flyway-core|"org\.testcontainers:(junit-jupiter|postgresql)"' src gradle build.gradle.kts`
  returns no matches.
- `git status --short` lists only the files in Scope.

### Completion criteria

- The worktree has the Scope changes uncommitted, and nothing else.
- The full build passes with 56 tests and unchanged coverage gates.
- No test assertion changed except the `asText()`→`asString()` and
  `isUnprocessableEntity()`→`isUnprocessableContent()` renames.
- Final report includes the test count and any deprecation warnings seen.

## Phase 2: Record Permission-Service Migration Status In Service-Common Docs

### Workspace

.

### Goal

Update service-common's Boot 4 migration notes so they reflect that permission-service now builds
and passes its suite against `0.0.18-SNAPSHOT`. Replace the research doc's *unverified* items with
the observed results.

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
  `../permission-service/build/test-results/test/*.xml`.

### Execution steps

1. In `spring-boot-4-consumer-migration.md`, `## Status`, add one or two sentences saying that
   `permission-service` has been migrated locally, uncommitted on top of the recorded HEAD. It
   builds and passes its existing suite (state the test count) against the Maven Local snapshot,
   and nothing has been deployed.
2. In `spring-boot-4-permission-service-research.md`, make these replacements, keeping the doc a
   research note:
   - Replace the *unverified* Flyway note with the observed result: `spring-boot-starter-flyway`
     is required, and without it the `Flyway` bean is missing.
   - Replace the SpringDoc `status` enum note with: the generated OpenAPI is byte-identical and
     the duplicated enum is unchanged.
   - Replace the stale-Jackson-key note with: the key is translated, so its stale-key behavior is
     moot.
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

- Both docs reflect the verified permission-service state.
