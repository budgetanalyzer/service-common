# Spring Boot 4 Review Remediation

Address the review of the `spring-boot-4-upgrade` branch against `main` while preserving the
existing integration coverage:

1. Main code still uses the deprecated Spring Framework 7 constant `HttpStatus.UNPROCESSABLE_ENTITY`.
2. `AGENTS.md` and `README.md` say the shared security chain backs off on both stacks. That is
   true only for reactive.
3. CI uploads only the ordinary `test` task's XML results, omitting both smoke suites and their
   HTML reports. Generated publication metadata is also absent from the build artifacts.
4. The README and the build comment say the smoke suites resolve "the published library". They
   actually resolve the project's own variants, so generated POM and module metadata is never
   exercised.

Keep the OpenAPI integration tests on both isolated classpaths. Unit tests that invoke a customizer
directly do not replace verification of SpringDoc annotation processing, customizer execution,
and the final document served over HTTP. The previous proposal to trim the smoke suites is withdrawn.

Plan-wide constraints:

- Keep runtime behavior unchanged. Both 422 constants have the value 422, and every security-chain
  fix is documentation only. Do not change `ClaimsHeaderSecurityConfig` or
  `ReactiveClaimsHeaderSecurityConfig`.
- The only existing test edits in scope are the 422 constant swaps in the three `src/test`
  assertions listed in Phase 1. Do not modify, weaken, or delete any other existing test.
  Leave both smoke source trees, including all assertions, helpers, controllers, and annotations,
  unchanged. CI must collect their results without changing how the tests run.
- Keep the smoke suites as permanent `check`-wired suites, and keep one source set per stack. Do
  not add a shared fixture source set: the fixtures are tiny, the controllers legitimately differ
  (blocking vs `Mono`), and a stack-neutral source set would need extra compile wiring.
- Generate and archive publication POM and Gradle module metadata in CI for inspection, but do not
  add a test that consumes the generated publication. Archiving metadata does not validate consumer
  resolution; consumer builds against `publishToMavenLocal` remain that separate check.
- Do not rewrite historical Phase records in `docs/plans/spring-boot-4-upgrade.md` or
  `docs/plans/spring-boot-4-consumer-migration.md`. Only append to the consumer-migration
  "Consumer Actions" list.
- Do not run git write operations.
- Use repository-relative paths only.

## Phase 1: Replace deprecated HttpStatus.UNPROCESSABLE_ENTITY

### Workspace

.

### Goal

Make main and test code compile without deprecation warnings for `HttpStatus.UNPROCESSABLE_ENTITY`,
while `BaseOpenApiConfig` still maps both 422 constants to `APPLICATION_ERROR`.

### Scope

- `service-web/src/main/java/org/budgetanalyzer/service/servlet/api/ServletApiExceptionHandler.java`
  (the `@ResponseStatus` on the `BusinessException` handler, plus Javadoc at about lines 54 and 197)
- `service-web/src/main/java/org/budgetanalyzer/service/api/ApiExceptionHandler.java` (the
  `ResolvedError` for `BusinessException`, about line 220)
- `service-web/src/main/java/org/budgetanalyzer/service/config/BaseOpenApiConfig.java`
  (`getTypeFromHttpStatus`, about line 336)
- Javadoc wording in:
  - `service-web/src/main/java/org/budgetanalyzer/service/reactive/api/ReactiveApiExceptionHandler.java`
  - `service-web/src/main/java/org/budgetanalyzer/service/exception/BusinessException.java`
  - `service-web/src/main/java/org/budgetanalyzer/service/api/ApiErrorType.java`
- `docs/error-handling.md` (about lines 105 and 151)
- These three authorized test assertions:
  - `service-web/src/test/java/org/budgetanalyzer/service/api/ApiExceptionHandlerTest.java` (about line 82)
  - `service-web/src/test/java/org/budgetanalyzer/service/reactive/api/ReactiveApiExceptionHandlerTest.java` (about lines 97 and 118)
- A new bullet in "## Consumer Actions" of `docs/plans/spring-boot-4-consumer-migration.md`

### Non-goals

- Do not change any other status code, error type, or response body.
- Do not change `BaseOpenApiConfigTest`. Its
  `@EnumSource(names = {"UNPROCESSABLE_ENTITY", "UNPROCESSABLE_CONTENT"})` uses strings, so it
  raises no deprecation warning, and it is the regression guard that both constants still map to
  `APPLICATION_ERROR`.
- Do not edit consumer repositories. A grep of the current consumer main branches found no
  `UNPROCESSABLE_ENTITY` usage.

### Required context

- `docs/error-handling.md`
- `docs/versioning-and-compatibility.md` (`buildExampleApiErrorResponse` is a `protected` API, so
  subclasses may still pass the deprecated constant)
- `docs/plans/spring-boot-4-consumer-migration.md`, section "## Consumer Actions"
- Facts from Spring Framework 7.0.10:
  - `UNPROCESSABLE_CONTENT` and `UNPROCESSABLE_ENTITY` are distinct enum constants, both with
    value 422.
  - `UNPROCESSABLE_CONTENT` is declared first, so `HttpStatus.valueOf(422)` returns
    `UNPROCESSABLE_CONTENT`.
  - AssertJ `isEqualTo` on the enum compares identity, so assertions must name the constant the
    code actually returns.

### Execution steps

1. In `ServletApiExceptionHandler`, change `@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)`
   to `HttpStatus.UNPROCESSABLE_CONTENT`.
2. In `ApiExceptionHandler`, change the `BusinessException` `ResolvedError` to
   `HttpStatus.UNPROCESSABLE_CONTENT`.
3. In `BaseOpenApiConfig.getTypeFromHttpStatus`:
   - Normalize the argument with `HttpStatus.valueOf(httpStatus.value())` before the `switch`.
   - Replace the combined `case HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.UNPROCESSABLE_CONTENT`
     with `case HttpStatus.UNPROCESSABLE_CONTENT`.
   - Leave every other case unchanged.
4. Change "422 Unprocessable Entity" to "422 Unprocessable Content" in the Javadoc of
   `ServletApiExceptionHandler`, `ReactiveApiExceptionHandler`, `BusinessException`, and
   `ApiErrorType`, and in `docs/error-handling.md`.
5. In `ApiExceptionHandlerTest` and `ReactiveApiExceptionHandlerTest`, change only the expected
   constant in the three `isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)` assertions to
   `HttpStatus.UNPROCESSABLE_CONTENT`. Change nothing else in those tests.
6. Append one bullet to "## Consumer Actions" in `docs/plans/spring-boot-4-consumer-migration.md`.
   It should say:
   - business-rule 422 responses now carry `HttpStatus.UNPROCESSABLE_CONTENT`;
   - code or tests that compare against `HttpStatus.UNPROCESSABLE_ENTITY` should use the new
     constant or compare `value()`;
   - the wire status is still 422.

### Implementation notes

- Normalizing through `HttpStatus.valueOf` keeps the deprecated constant unnamed and still
  handles subclasses that pass it to the `protected` `buildExampleApiErrorResponse`.
- Do not suppress deprecation warnings; remove the usages instead.
- Spring's own reason phrase for 422 is now "Unprocessable Content", which is why the
  documentation wording changes.

### Validation

- `rg -n "UNPROCESSABLE_ENTITY" service-web/src` matches only the `@EnumSource` string in
  `BaseOpenApiConfigTest`.
- `rg -n "Unprocessable Entity" service-web/src docs/error-handling.md` returns no matches.
- `./gradlew :service-web:compileJava :service-web:compileTestJava --rerun-tasks` prints no
  deprecation note or warning for `service-web`.
- `./gradlew :service-web:test` passes.

### Completion criteria

- No main or test Java source names `HttpStatus.UNPROCESSABLE_ENTITY`.
- `BaseOpenApiConfigTest` passes unchanged, including both `@EnumSource` cases.
- The Javadoc, `docs/error-handling.md`, and the consumer-migration Consumer Actions list use the
  "Unprocessable Content" / `UNPROCESSABLE_CONTENT` wording.
- `service-web` unit and integration tests pass.

## Phase 2: Correct security-chain documentation

### Workspace

.

### Goal

Make `AGENTS.md` and `README.md` describe the actual servlet and reactive security-chain
behavior. The code Javadoc is already correct and stays as it is.

### Scope

- `AGENTS.md`:
  - the bullet under "Shared Configuration" (about line 488)
  - the bullet under "Summary: Action Required by Consuming Services" (about line 556)
- `README.md`, the paragraph after "No component scanning needed" (about lines 179-181)

### Non-goals

- Do not change `ClaimsHeaderSecurityConfig`, `ReactiveClaimsHeaderSecurityConfig`, or any test.
- Do not add `@ConditionalOnMissingBean` to the servlet chain. `permission-service` depends on
  coexistence: it defines an `@Order(0)` chain with a `securityMatcher` alongside the shared chain.
- Do not edit `docs/spring-boot-conventions.md`. Its security-chain sentence is about OpenAPI
  inference and is already correct.

### Required context

- `../orchestration/docs/agents-md-checkstyle.md`. Read it and apply it before editing `AGENTS.md`:
  - use imperative guardrails;
  - prefer patterns to inventories;
  - use plain paths;
  - do not use `@` imports.
- `service-web/src/main/java/org/budgetanalyzer/service/security/ClaimsHeaderSecurityConfig.java`.
  Its class Javadoc describes coexistence. The `securityFilterChain` bean is ordered at
  `SecurityFilterProperties.BASIC_AUTH_ORDER` and has no `@ConditionalOnMissingBean`.
- `service-web/src/main/java/org/budgetanalyzer/service/security/ReactiveClaimsHeaderSecurityConfig.java`.
  `securityWebFilterChain` is `@ConditionalOnMissingBean(SecurityWebFilterChain.class)`, and
  `@EnableReactiveMethodSecurity` stays active.
- The executable evidence:
  - `service-web/src/servletSmokeTest/java/org/budgetanalyzer/smoke/servlet/ServletConsumerSecurityChainSmokeTest.java`
  - `service-web/src/reactiveSmokeTest/java/org/budgetanalyzer/smoke/reactive/ReactiveConsumerSecurityChainSmokeTest.java`

### Execution steps

1. Read the AGENTS.md checkstyle, both security config classes, and both ConsumerSecurityChain
   smoke tests. Confirm the behavior described in Required context.
2. Replace the `AGENTS.md` "Shared Configuration" bullet with two short imperative bullets:
   - **Servlet:** the shared chain coexists with consumer chains. To own specific paths, add a
     lower-`@Order` `SecurityFilterChain` with a `securityMatcher`; unmatched requests fall through
     to the shared chain. A lower-order chain without a matcher shadows the shared chain for every
     request.
   - **Reactive:** the shared `SecurityWebFilterChain` backs off when any `SecurityWebFilterChain`
     bean exists. The consumer chain then owns all authentication and authorization rules, and
     `@PreAuthorize` is still enforced.
3. Replace the `AGENTS.md` summary bullet with an accurate one-line version. Example: "Servlet
   consumer chains coexist with the shared chain through `@Order` and `securityMatcher`; any
   reactive `SecurityWebFilterChain` replaces the shared reactive chain."
4. Add a consultation trigger pointing to the two security config classes and the two
   ConsumerSecurityChain smoke tests, as plain paths. If an adjacent Discovery block fits better,
   add them there instead.
5. Rewrite the `README.md` paragraph with the same servlet and reactive distinction, in prose.

### Implementation notes

- Keep the AGENTS.md edits minimal and local. Do not restructure the surrounding sections.
- Describe behavior, not version history.

### Validation

- `rg -n "backs off" AGENTS.md README.md` matches only reactive-specific statements.
- `rg -n "override the shared default chain on both" AGENTS.md` returns no matches.
- Reread the edited lines against `ClaimsHeaderSecurityConfig` and
  `ReactiveClaimsHeaderSecurityConfig`. Each claim should match the code and the two
  ConsumerSecurityChain smoke tests.
- `git diff --stat` shows changes only to `AGENTS.md` and `README.md` in this phase.

### Completion criteria

- `AGENTS.md` and `README.md` describe servlet coexistence and reactive back-off separately and
  accurately, and the `AGENTS.md` edits follow the checkstyle.
- No code or test files changed in this phase.

## Phase 3: Preserve smoke coverage and correct publication wording

### Workspace

.

### Goal

Describe the permanent isolated smoke suites accurately, preserving every existing test and the
real-HTTP OpenAPI integration coverage. Correct the "published library" wording.

### Scope

- `README.md`, the smoke-suite paragraph under the JaCoCo section (about lines 272-278)
- `service-web/build.gradle.kts`, the comment above `testing { suites { ... } }` (about lines 36-38)

### Non-goals

- Do not change any test source or resource, including either smoke suite or its fixtures.
- Do not change suite registration, dependencies, or `check` wiring in `service-web/build.gradle.kts`.
- Do not add a shared fixture source set, and do not add a publication-consuming test.

### Required context

- `docs/testing-patterns.md`
- Both `ServletSmokeTest` and `ReactiveSmokeTest`, their `SmokeItemController` fixtures, and both
  `ConsumerSecurityChainSmokeTest` classes under the corresponding smoke source trees.
- `service-web/src/test/java/org/budgetanalyzer/service/config/BaseOpenApiConfigTest.java`:
  direct customizer tests cover logic but do not replace the smoke suites' SpringDoc integration.
- `service-web/build.gradle.kts` suite registration. Each suite uses `implementation(project())`.
- The publication config in the root and module build files:
  - `from(components["java"])` publishes Gradle module metadata generated from the same api and
    runtime variants that `implementation(project())` resolves.
  - `versionMapping { allVariants { fromResolutionResult() } }` writes resolved versions into the
    POM and module files. The suites do not exercise those files.

### Execution steps

1. Confirm and preserve the complete OpenAPI integration checks on each stack:
   - anonymous `GET /v3/api-docs` succeeds with the expected API title;
   - explicit controller `@ApiResponse` descriptions survive generation;
   - generated response-code sets and 400/401/403/500/503 schema references and examples match
     the existing assertions;
   - unsecured operations do not receive inappropriate generated security responses;
   - `ApiErrorResponse` and `FieldError` schema properties are present.
   Keep `assertStandardErrorResponse`, `propertyNames`, both explicit controller annotations,
   and `EXPLICIT_NOT_FOUND_DESCRIPTION`. Preserve every other smoke test and assertion as well,
   including the successful-response mapper check and all JSON-shape checks.
2. Replace the README smoke-suite sentences:
   - State that each suite depends on the project's own api and runtime variants through
     `implementation(project())`, plus a single web stack, Spring Security, and the matching
     SpringDoc starter, without JPA or the combined test classpath.
   - Describe the retained checks: startup and classpath isolation, shared security and method
     security wiring, `ApiErrorResponse` handling, actuator probes and Prometheus, and SpringDoc
     serving the shared error documentation, including explicit response preservation and schema
     and example contents.
   - Explain that `src/<suiteName>/java` and `resources` are Gradle JVM test suite conventions.
     The isolated suites are permanent guards against optional-dependency and auto-configuration
     regressions; selecting an application type on the combined test classpath is not equivalent.
   - State that the suites do not exercise the generated POM or module files, or the versions
     written by `versionMapping`. Consumer builds against `publishToMavenLocal` are required to
     verify published metadata; the smoke suites do not satisfy that gate.
   - Keep the sentence that neither suite adds published dependencies.
3. Change the `service-web/build.gradle.kts` comment the same way. Replace "consumes this library
   the way a service does (published api/runtime dependencies only)" with wording that says the
   suite depends on this project's api and runtime variants, the same variants the published
   module metadata is generated from.

### Implementation notes

- The two suites stay intentionally parallel. Duplication alone is not grounds for removing
  integration assertions or adding a shared fixture source set.
- Each suite retains 11 tests in `*SmokeTest` plus two ConsumerSecurityChain tests: 13 total.

### Validation

- `./gradlew :service-web:servletSmokeTest :service-web:reactiveSmokeTest` passes.
- `git diff --exit-code HEAD -- service-web/src/servletSmokeTest service-web/src/reactiveSmokeTest`
  succeeds: both complete source trees remain unchanged.
- `rg -n "published library" README.md service-web/build.gradle.kts` returns no matches.
- `git diff --stat -- service-web/src/test` shows no changes from this phase.

### Completion criteria

- Both smoke suites pass with 13 tests each and no removed or weakened assertions.
- The README and the build comment accurately describe what the suites resolve and what they do
  not verify.

## Phase 4: Collect smoke reports and publication metadata in CI

### Workspace

.

### Goal

Make the build's downloadable artifacts include ordinary and smoke-suite results, readable HTML
reports, and generated publication metadata, including available diagnostics when the build fails.

### Scope

- `.github/workflows/build.yml`
- `README.md`, the testing/coverage and CI artifact documentation adjacent to the smoke-suite
  paragraph updated in Phase 3.

### Non-goals

- Do not change test sources, Gradle suite wiring, coverage gates, or publication definitions.
- Do not change release or snapshot publication workflows, action versions, permissions, or triggers.
- Do not introduce a published-artifact consumer test or a new remote publishing step.
- Do not dispatch a workflow or publish artifacts as part of local validation.

### Required context

- `.github/workflows/build.yml`, especially `Build with Gradle`, `Upload test results`, and
  `Upload build artifacts`.
- `build.gradle.kts`, `service-web/build.gradle.kts`, and both platform build files for suite
  names, `check` dependencies, and the `mavenJava` publication.
- `README.md`, the testing/coverage documentation.
- `docs/versioning-and-compatibility.md`, publishing boundaries.

### Execution steps

1. Keep `./gradlew build` as the test gate. Do not use `continue-on-error` or suppress its failure.
2. Expand the existing `test-results` artifact, preserving `if: always()` and seven-day retention,
   to include XML from every test task and HTML reports with their supporting files:

   ```yaml
   path: |
     **/build/test-results/*/*.xml
     **/build/reports/tests/**
   ```

   Preserve module and task paths so `service-core:test`, `service-web:test`, `servletSmokeTest`,
   and `reactiveSmokeTest` remain distinguishable. XML carries suite/test names, counts, failures,
   skips, durations, and captured output; HTML provides the navigable report. Do not upload Gradle's
   internal binary result files. Use `if-no-files-found: warn`: a compilation failure can legitimately
   prevent reports from being generated, and the original build failure must remain visible.
3. Add a metadata-generation step after the build and report upload, using `if: always()`:

   ```bash
   ./gradlew generatePomFileForMavenJavaPublication generateMetadataFileForMavenJavaPublication
   ```

   These tasks must generate the POM and `.module` metadata for all four artifacts without invoking
   any publish task. Do not suppress generation failures. Keep report upload before this step so a
   metadata-generation failure cannot prevent collection of already produced test diagnostics.
4. Add a separate `publication-metadata` upload with `if: always()`, seven-day retention, and
   `if-no-files-found: warn`, using the workflow's existing pinned upload-artifact action:

   ```yaml
   path: |
     **/build/publications/mavenJava/pom-default.xml
     **/build/publications/mavenJava/module.json
   ```

   Preserve module directories to distinguish all four publications. Collect available files after
   a failed generation step without presenting partial output as a successful validation.
5. Keep the existing JAR artifact and Maven Local publication behavior. Update the README to name
   the report and metadata artifacts, their contents, retention, and failure-time availability.
   Explain that archiving generated POM/module files makes them reviewable but does not exercise
   consumer dependency resolution or change the separate consumer-integration release gate.

### Implementation notes

- Use the existing upload action; no third-party test reporting service or extra permissions are
  needed. GitHub associates uploaded artifacts with the workflow run and commit.
- The wildcard for XML deliberately covers all task names, including future isolated suites.
- `if: always()` collects available diagnostics; it does not guarantee files exist when compilation
  or dependency resolution failed before producing them.

### Validation

- Run `./gradlew build generatePomFileForMavenJavaPublication generateMetadataFileForMavenJavaPublication`.
- Confirm the report upload patterns match XML and HTML output for all four test tasks, including
  both ConsumerSecurityChain test classes. Inspect XML suite metadata and HTML entry points.
- Confirm the metadata upload patterns match eight files: one POM and one `module.json` for each
  of `spring-platform`, `spring-cloud-platform`, `service-core`, and `service-web`.
- Validate workflow syntax with `actionlint` if installed; otherwise inspect YAML structure and
  GitHub expressions and explicitly record that actionlint was unavailable.
- Inspect failure paths: a failed build still reaches report upload and metadata steps, failed
  generation still reaches metadata upload, and neither failure is hidden. Review these conditions
  without changing or deliberately breaking application tests.
- Confirm workflow changes are limited to artifact collection and metadata generation. Local
  validation proves generation and path matching; hosted uploads require the next CI run.

### Completion criteria

- CI is configured to archive ordinary and smoke-suite XML/HTML reports and publication metadata,
  preserving module/task identity and collecting available diagnostics on failure.
- Local generation and artifact-pattern checks pass; hosted-run validation status is recorded
  accurately without requiring a workflow dispatch.
- README documentation matches the workflow, and no test or publication behavior is weakened.

## Phase 5: Full validation

### Workspace

.

### Goal

Confirm that the combined remediation builds cleanly, passes every check, and leaves documentation
consistent.

### Scope

- The whole repository build, all test suites, and a final consistency review of the documents
  edited in Phases 1-4, including CI artifact collection.

### Non-goals

- Do not make new functional changes. Fix only defects that this validation finds in Phase 1-4
  work.
- Do not run git write operations.

### Required context

- `AGENTS.md` build commands.
- The validation sections of Phases 1-4.

### Execution steps

1. Run `./gradlew clean build`. Fix any Checkstyle, Spotless, Javadoc, or coverage failure only
   within remediation scope. Do not apply broad formatting changes to protected test sources.
2. Run the Phase 4 metadata-generation tasks and repeat its artifact-pattern and workflow checks.
3. Rerun the grep and preservation checks from Phases 1-3:
   - `rg -n "UNPROCESSABLE_ENTITY" service-web/src` matches only the `BaseOpenApiConfigTest`
     `@EnumSource` string.
   - `rg -n "Unprocessable Entity" service-web/src docs/error-handling.md` returns no matches.
   - `git diff --exit-code HEAD -- service-web/src/servletSmokeTest service-web/src/reactiveSmokeTest`
     succeeds. All smoke tests, fixtures, and OpenAPI assertions remain unchanged.
   - Confirm the only `src/test` changes are the three authorized Phase 1 assertion constant swaps.
   - `rg -n "published library" README.md service-web/build.gradle.kts` returns no matches.
4. Confirm the `./gradlew clean build` output has no deprecation note for `service-web` main or
   test compilation.
5. Record the test counts for `service-core:test`, `service-web:test`,
   `service-web:servletSmokeTest`, and `service-web:reactiveSmokeTest`. The baseline before this
   plan was 218, 573, 13, and 13.
6. Run `git diff --stat` and confirm that only the files named in Phases 1-4 changed.

### Implementation notes

- Counts stay at 218 for `service-core:test`, 573 for `service-web:test`, and 13 for each smoke
  suite: 817 total, with zero failures, errors, or skips. Phase 1 only swaps assertion constants;
  no tests are removed or added by this plan.
- If a count differs, explain why before completing.

### Validation

- `./gradlew clean build` succeeds.
- All grep checks pass.
- The test counts are recorded and explained.
- All four test tasks' reports and all four publications' metadata match the CI upload patterns.

### Completion criteria

- The full build is green with no deprecation warnings from `UNPROCESSABLE_ENTITY`.
- The documentation matches the code.
- Both smoke source trees and their complete OpenAPI integration coverage remain unchanged.
- CI collects smoke diagnostics and publication metadata with the limitations documented.
- The changed-file set is limited to the Phase 1-4 scope.
