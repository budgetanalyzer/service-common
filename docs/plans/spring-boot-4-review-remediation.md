# Spring Boot 4 Review Remediation

Fix four findings from the review of the `spring-boot-4-upgrade` branch against `main`:

1. Main code still uses the deprecated Spring Framework 7 constant `HttpStatus.UNPROCESSABLE_ENTITY`.
2. `AGENTS.md` and `README.md` say the shared security chain backs off on both stacks. That is
   true only for reactive.
3. The servlet and reactive smoke suites are near-duplicate files of about 290 lines each. Their
   detailed OpenAPI and JSON-shape assertions overlap with `src/test`.
4. The README and the build comment say the smoke suites resolve "the published library". They
   actually resolve the project's own variants, so generated POM and module metadata is never
   exercised.

Plan-wide constraints:

- Keep runtime behavior unchanged. Both 422 constants have the value 422, and every security-chain
  fix is documentation only. Do not change `ClaimsHeaderSecurityConfig` or
  `ReactiveClaimsHeaderSecurityConfig`.
- The user has explicitly authorized these test edits and no others:
  - the 422 constant swap in the three `src/test` assertions listed in Phase 1;
  - the smoke-suite trimming in Phase 3.
  Do not modify, weaken, or delete any other existing test.
- Keep the smoke suites as permanent `check`-wired suites, and keep one source set per stack. Do
  not add a shared fixture source set: the fixtures are tiny, the controllers legitimately differ
  (blocking vs `Mono`), and a stack-neutral source set would need extra compile wiring.
- Do not add a test that consumes the generated publication. Correct the wording instead. Consumer
  builds against `publishToMavenLocal` remain the check for published metadata.
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

## Phase 3: Trim smoke suites and correct publication wording

### Workspace

.

### Goal

Reduce each smoke suite to checks that only a single-stack consumer classpath can catch:

- startup and classpath isolation
- auto-configuration wiring of the shared security chain and method security
- the stack's error handler producing `ApiErrorResponse`
- actuator probes and Prometheus
- the stack's SpringDoc module serving shared error documentation

Remove assertions that `src/test` already covers. Also correct the "published library" wording.

### Scope

- `service-web/src/servletSmokeTest/java/org/budgetanalyzer/smoke/servlet/ServletSmokeTest.java`
- `service-web/src/servletSmokeTest/java/org/budgetanalyzer/smoke/servlet/SmokeItemController.java`
- `service-web/src/reactiveSmokeTest/java/org/budgetanalyzer/smoke/reactive/ReactiveSmokeTest.java`
- `service-web/src/reactiveSmokeTest/java/org/budgetanalyzer/smoke/reactive/SmokeItemController.java`
- `README.md`, the smoke-suite paragraph under the JaCoCo section (about lines 272-278)
- `service-web/build.gradle.kts`, the comment above `testing { suites { ... } }` (about lines 36-38)

### Non-goals

- Do not change `ServletConsumerSecurityChainSmokeTest` or `ReactiveConsumerSecurityChainSmokeTest`.
- Do not change any `src/test` test.
- Do not change suite registration, dependencies, or `check` wiring in `service-web/build.gradle.kts`.
- Do not add a shared fixture source set, and do not add a publication-consuming test.
- Do not change `CreateSmokeItemRequest`, `SmokeItemResponse`, `SmokeOpenApiConfig`, the smoke
  applications, or `application.yml`.

### Required context

- `docs/testing-patterns.md`
- Coverage in `service-web/src/test` that makes the removed smoke assertions redundant:
  - `BaseOpenApiConfigTest`:
    - preservation of explicit `@ApiResponse`
    - per-method 400/404/500/503 sets
    - error-type examples, and the absence of `code` in examples
    - no 401/403 without `@PreAuthorize`
    - `ApiErrorResponse` / `FieldError` schema properties
  - `ApiErrorResponseTest` and `FieldErrorTest`: `NON_NULL` omission and property shape with
    Boot's mapper
  - `ExceptionHandlingIntegrationTest`, `ApiExceptionHandlerTest`, and
    `ReactiveApiExceptionHandlerTest`: status, type, message, and code mapping
- `service-web/build.gradle.kts` suite registration. Each suite uses `implementation(project())`.
- The publication config in the root and module build files:
  - `from(components["java"])` publishes Gradle module metadata generated from the same api and
    runtime variants that `implementation(project())` resolves.
  - `versionMapping { allVariants { fromResolutionResult() } }` writes resolved versions into the
    POM and module files. The suites do not exercise those files.

### Execution steps

1. Before removing an assertion, find a `src/test` test that covers the same behavior. If none
   exists, keep that assertion in the smoke suite and mention it in the phase result.
2. Apply the same changes to `ServletSmokeTest` and `ReactiveSmokeTest`:
   - Delete `shouldReturnSuccessfulResponse`. It checks Boot's mapper null handling, not the library.
   - In `shouldReturnUnauthorizedWithoutClaims` and `shouldReturnForbiddenWhenPermissionIsMissing`,
     remove the `propertyNames(...).containsExactly(...)` assertions. Keep the status, `type`, and
     `message` assertions.
   - Rename `shouldOmitAbsentOptionalFieldsFromNotFoundResponse` to
     `shouldReturnNotFoundApiErrorResponse` and update its `@DisplayName`. Remove the
     `propertyNames` assertion. Keep the status, `type`, and `message` assertions.
   - In `shouldIncludeErrorCodeInBusinessResponse`, remove the `propertyNames` assertion. Keep
     the `UNPROCESSABLE_CONTENT` status and the `type`, `message`, and `code` assertions. It is
     the only real-HTTP check of the Phase 1 business 422 path on each stack.
   - In `shouldReturnValidationFieldErrors`:
     - remove both `propertyNames` assertions;
     - keep the status, `type`, and first-field-error `field` and `message` assertions;
     - update the `@DisplayName` so it no longer mentions absent optional properties.
   - Reduce `shouldGenerateOpenApiDocumentationWithSharedErrorResponses` to these checks:
     - anonymous `GET /v3/api-docs` returns 200;
     - `info.title` matches the suite's `SmokeOpenApiConfig`;
     - the `POST /api/smoke/items` responses for 401 and 403 reference
       `#/components/schemas/ApiErrorResponse`;
     - `/components/schemas` contains `ApiErrorResponse`.
   - Reduce `assertStandardErrorResponse` to the schema `$ref` check, or inline it.
   - Delete `propertyNames` and any imports that become unused.
   - Leave the startup and classpath test, `shouldAllowMethodSecuredRequestWithPermission`, the
     health-probe test, and the Prometheus test unchanged.
3. In both `SmokeItemController` classes, remove the two `@ApiResponse` annotations on `getItem`,
   the `EXPLICIT_NOT_FOUND_DESCRIPTION` constant, and the unused `ApiResponse` import. Keep the
   `business-error` endpoints. Both suites still use them, and the servlet consumer-chain test
   uses the servlet one.
4. Replace the README smoke-suite sentences:
   - State that each suite depends on the project's own api and runtime variants through
     `implementation(project())`, plus a single web stack, Spring Security, and the matching
     SpringDoc starter, without JPA or the combined test classpath.
   - Describe the trimmed checks: startup and classpath isolation, shared security and method
     security wiring, `ApiErrorResponse` handling, actuator probes and Prometheus, and SpringDoc
     serving the shared error documentation.
   - State that the suites do not exercise the generated POM or module files, or the versions
     written by `versionMapping`. Consumer builds against `publishToMavenLocal` cover published
     metadata.
   - Keep the sentence that neither suite adds published dependencies.
5. Change the `service-web/build.gradle.kts` comment the same way. Replace "consumes this library
   the way a service does (published api/runtime dependencies only)" with wording that says the
   suite depends on this project's api and runtime variants, the same variants the published
   module metadata is generated from.

### Implementation notes

- The two suites stay intentionally parallel. Keep matching test names and structure so a
  reviewer can diff them.
- Use AssertJ only.
- Do not add JSON property-order assertions anywhere.
- Expect each suite to drop from 11 to 10 tests in `*SmokeTest`. The ConsumerSecurityChain tests
  are unchanged.

### Validation

- `./gradlew :service-web:servletSmokeTest :service-web:reactiveSmokeTest` passes.
- `rg -n "propertyNames|EXPLICIT_NOT_FOUND_DESCRIPTION|shouldReturnSuccessfulResponse" service-web/src/*SmokeTest`
  returns no matches.
- `rg -n "published library" README.md service-web/build.gradle.kts` returns no matches.
- `git diff --stat -- service-web/src/test` shows no changes from this phase.

### Completion criteria

- Both smoke suites pass and keep only single-stack-relevant checks.
- Every removed assertion has a named covering test in `src/test`, or was kept.
- The README and the build comment accurately describe what the suites resolve and what they do
  not verify.

## Phase 4: Full validation

### Workspace

.

### Goal

Confirm that the combined remediation builds cleanly, passes every check, and leaves documentation
consistent.

### Scope

- The whole repository build, all test suites, and a final consistency review of the documents
  edited in Phases 1-3.

### Non-goals

- Do not make new functional changes. Fix only defects that this validation finds in Phase 1-3
  work.
- Do not run git write operations.

### Required context

- `AGENTS.md` build commands.
- The validation sections of Phases 1-3.

### Execution steps

1. Run `./gradlew clean spotlessApply`.
2. Run `./gradlew clean build`. Fix any Checkstyle, Spotless, Javadoc, or coverage failure in
   remediation work.
3. Rerun the grep checks from Phases 1-3:
   - `rg -n "UNPROCESSABLE_ENTITY" service-web/src` matches only the `BaseOpenApiConfigTest`
     `@EnumSource` string.
   - `rg -n "Unprocessable Entity" service-web/src docs/error-handling.md` returns no matches.
   - `rg -n "propertyNames|EXPLICIT_NOT_FOUND_DESCRIPTION" service-web/src/*SmokeTest` returns no
     matches.
   - `rg -n "published library" README.md service-web/build.gradle.kts` returns no matches.
4. Confirm the `./gradlew clean build` output has no deprecation note for `service-web` main or
   test compilation.
5. Record the test counts for `service-core:test`, `service-web:test`,
   `service-web:servletSmokeTest`, and `service-web:reactiveSmokeTest`. The baseline before this
   plan was 218, 573, 13, and 13.
6. Run `git diff --stat` and confirm that only the files named in Phases 1-3 changed.

### Implementation notes

- `service-web:test` should still report 573 tests, because Phase 1 only swaps assertion
  constants. Each smoke suite should report 12: one removed test plus the two unchanged
  ConsumerSecurityChain tests.
- If a count differs, explain why before completing.

### Validation

- `./gradlew clean build` succeeds.
- All grep checks pass.
- The test counts are recorded and explained.

### Completion criteria

- The full build is green with no deprecation warnings from `UNPROCESSABLE_ENTITY`.
- The documentation matches the code.
- The changed-file set is limited to the Phase 1-3 scope.
