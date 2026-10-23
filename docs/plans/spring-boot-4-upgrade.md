# Spring Boot 4 Upgrade Plan

Upgrade `service-common` from Spring Boot 3.5.16 to the latest compatible Spring Boot 4 dependency
set, resolving the major-migration items on the Renovate Dependency Dashboard (issue #56). This
first iteration changes only this repository. A later integration plan moves the consumers onto it.

## Status

Reviewed execution plan. Migration has not started.

## Versioning

- The version literal in `build.gradle.kts` stays `0.0.18-SNAPSHOT`. That snapshot line becomes
  the Spring Boot 4 line. Do not change the version in any phase.
- This plan records the user-approved exception to the normal backwards-compatibility,
  deprecation, and major-version rules for this development snapshot only. Jackson public API
  changes and the framework/BOM changes below are intentional; do not add compatibility shims.
  This exception does not authorize a stable release, consumer upgrades, or deployment.
- The four current consumer repositories pin the published `0.0.17` release. Recheck their
  checked-in coordinates before publishing; do not assume this remains true throughout the work.
  Discover consumers from the published artifact coordinates in sibling catalogs (Phase 5's
  validation command excludes this library itself). Every discovered consumer must pin exactly
  `0.0.17`; a different, dynamic, or missing coordinate fails the check. Do not edit consumers.
- Phase 5 publishes locally only. The same prerequisite applies before any later merge or manual
  workflow dispatch that would refresh `0.0.18-SNAPSHOT` in GitHub Packages. Git operations and
  remote publishing are outside this plan.
- Completion means a verified library development snapshot. The later consumer integration plan
  must establish the stable release version, migrate and test all consumers, and document rollout
  and rollback before the library is considered ready for a stable release.

## Target Dependency Set

Versions are the latest releases on Maven Central as of 2026-10-22:

| Catalog key | Current | Target | Notes |
| --- | --- | --- | --- |
| `springBoot` | 3.5.16 | 4.1.2 | Framework 7.0.10, Security 7.1.2, Hibernate 7.4, Jackson 3.1.7, JUnit 6.0.3, Micrometer 1.17, Tomcat 11 |
| `springCloud` | 2025.0.3 | 2025.1.4 | Oakwood; 2025.1.2+ is required for Boot 4.1.x |
| `springModulith` | 1.4.13 | 2.1.2 | 2.1 is the Boot 4.1 line |
| `springdoc` | 2.9.1 | 3.1.1 | 3.x is required for Boot 4; 3.1.x targets Boot 4.1 |
| `testcontainers` | 1.21.4 | removed | Boot 4.1.2 imports `testcontainers-bom` 2.0.5; old artifact names stop at 1.21.4 |
| `shedlock` | 6.10.0 | 7.10.1 | 7.x is tested with Boot 4.x |
| `wiremock` | 3.13.2 | 3.13.2 | Unchanged |
| `awaitility` | 4.3.0 | 4.3.0 | Unchanged; Boot also manages 4.3.0 |

Out of scope: the GitHub Actions majors from the dashboard (`actions/checkout` v7,
`actions/setup-java` v6, Ubuntu 26.04 runners). Spotless, Checkstyle, google-java-format, JaCoCo,
OpenCSV, and Gradle 9.8.2 need no change for Boot 4; Boot 4 requires Gradle 8.14+.

## Gotchas Relevant To This Repository

- **Modular autoconfigure jars.** Boot 4 splits `spring-boot-autoconfigure` into per-technology
  modules with new packages. Relocations verified against the 4.1.2 jars:
  - `DataSourceAutoConfiguration` -> `org.springframework.boot.jdbc.autoconfigure`
  - `ErrorWebFluxAutoConfiguration` -> `org.springframework.boot.webflux.autoconfigure.error`
  - `ErrorWebExceptionHandler` -> `org.springframework.boot.webflux.error`
  - `SecurityProperties`, `SecurityAutoConfiguration` -> `org.springframework.boot.security.autoconfigure`
  - `EntityScan` -> `org.springframework.boot.persistence.autoconfigure`
  - `JacksonAutoConfiguration` -> `org.springframework.boot.jackson.autoconfigure`
  - `WebMvcAutoConfiguration`, `DispatcherServletAutoConfiguration` -> `org.springframework.boot.webmvc.autoconfigure`
  - `WebFluxAutoConfiguration` -> `org.springframework.boot.webflux.autoconfigure`
  - `AutoConfigureMockMvc` -> `org.springframework.boot.webmvc.test.autoconfigure`
  - `AutoConfigureWebTestClient` -> `org.springframework.boot.webtestclient.autoconfigure`
  - `EnvironmentPostProcessor` -> `org.springframework.boot` (the old `org.springframework.boot.env`
    type is deprecated). Update the `spring.factories` key along with the imports.
  - Condition annotations (`ConditionalOn*`) and `@AutoConfiguration` stay in
    `org.springframework.boot.autoconfigure`.
- **Starter renames.** `spring-boot-starter-web` is deprecated in favor of
  `spring-boot-starter-webmvc`. Each technology also has its own test starter
  (`spring-boot-starter-webmvc-test`, `-webflux-test`, `-security-test`, `-data-jpa-test`), and
  those bring `spring-boot-starter-test` transitively. `spring-boot-starter-classic` and
  `spring-boot-starter-test-classic` exist as a temporary bridge. This plan targets the modular
  starters and does not use the bridge.
- **Jackson 3.** Packages move from `com.fasterxml.jackson` to `tools.jackson`. Annotations stay in
  `com.fasterxml.jackson.annotation`. Boot auto-configures a `tools.jackson.databind.json.JsonMapper`
  bean and no Jackson 2 `ObjectMapper` bean, so every bean method here that injects
  `com.fasterxml.jackson.databind.ObjectMapper` fails at startup. Type renames we use:
  `SerializerProvider` -> `SerializationContext`, `JsonSerializer` -> `ValueSerializer`,
  `BeanSerializerModifier` -> `ValueSerializerModifier`, `TextNode` -> `StringNode`, and
  `JsonGenerator.writeFieldName` -> `writeName`. Serializer-modifier callbacks now accept
  `BeanDescription.Supplier` rather than `BeanDescription`; adapt property introspection too.
  `JavaTimeModule` is built in, so `jackson-datatype-jsr310` goes away. Mappers are immutable and
  built with `JsonMapper.builder()`.
  `JacksonException` is unchecked. Several defaults change, including
  `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` and the date features that moved to
  `DateTimeFeature`. These can reorder JSON output in `SafeLogger` and `ApiErrorResponse`.
  `spring-boot-jackson2` exists but is deprecated, and this plan does not use it.
- **Testing.** `@MockBean` and `@SpyBean` are removed (none are used here). `@SpringBootTest` no
  longer provides MockMvc, `TestRestTemplate`, or `WebTestClient` implicitly; the existing tests
  already declare `@AutoConfigureMockMvc` / `@AutoConfigureWebTestClient`. JUnit Jupiter moves to 6.
- **Testcontainers 2.** Module artifacts are renamed with a `testcontainers-` prefix (for example
  `org.testcontainers:testcontainers-postgresql`). JUnit 4 support is removed, and `PostgreSQLContainer`
  is non-generic in `org.testcontainers.postgresql`. Docker API 1.44 is the default, which is
  compatible with Docker 29. That makes the current 1.21.x Docker 29 pin rationale obsolete.
- **Actuator.** Liveness and readiness probes, and their health groups, are enabled by default.
  `/actuator/health/**` is already public in both claims-header security chains. The property names
  set by `PrometheusEndpointPostProcessor` and `ApplicationMetricTagPostProcessor` are still
  present and undeprecated in the 4.1.2 metadata.
- **Spring Security 7.** `@EnableReactiveMethodSecurity(useAuthorizationManager = true)` still
  exists and is not deprecated in 7.1.2. Keep the existing method-security configuration.
- **Spring Cloud.** 2025.1.0 is incompatible with Boot 4.0.1+, and Boot 4.1 needs 2025.1.2+. The
  repository Renovate rule that disables Cloud minor and major updates stays as-is; this plan
  performs the train change manually.

## References

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)
- [Migrating to Jackson 3](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)
- [Spring Cloud 2025.1 (Oakwood) announcement](https://spring.io/blog/2025/11/25/spring-cloud-2025-1-0-aka-oakwood-has-been-released/)
- [springdoc-openapi compatibility](https://springdoc.org/)
- [ShedLock compatibility table](https://github.com/lukas-krecan/ShedLock)
- Local: [versioning and compatibility](../versioning-and-compatibility.md),
  [dependency automation](../dependency-automation.md), [testing patterns](../testing-patterns.md)

## Plan-Wide Constraints

- Work only in this repository. Do not edit consumer services, orchestration, or
  budget-analyzer-web.
- Record public API changes, dependency changes, validation results, and consumer actions as they
  arise in `docs/plans/spring-boot-4-consumer-migration.md`, created in Phase 1. Include changed
  public supertypes and implemented interfaces, not just constructors and method parameters.
  Keep this execution plan unchanged while the session handler is running it.
- Update the nearest affected documentation in each phase. Canonical docs stay version-agnostic;
  release-specific details belong in the consumer migration notes. Before any `AGENTS.md` edit,
  read [AGENTS.md checkstyle](../../../orchestration/docs/agents-md-checkstyle.md).
- Do not modify, weaken, or delete existing tests to make them pass. Adapt imports and Jackson
  construction where the old API no longer exists. If an assertion fails because behavior changed,
  fix the production code to preserve behavior, or stop and record the decision.
- Keep the JaCoCo minimums in the root `build.gradle.kts` unchanged.
- Use real application beans and concrete test fixtures. Do not introduce Mockito bean overrides.
- Phases 1 and 2 intentionally leave later modules unbuildable; their scoped validation replaces
  the repository-wide build gate at those checkpoints. From Phase 3 onward, the full build must
  pass. Unresolved behavior changes are blockers, not successful completion with a note.
- Pure JPA only. No `org.hibernate.*` imports.
- No git write operations.

## Phase 1: Baseline And Dependency Wiring

### Workspace

.

### Goal

Establish the Boot 3 behavior baseline, then move the catalog, platforms, and module build scripts
to the target set without breaking Gradle configuration or dependency resolution.

### Scope

- `gradle/libs.versions.toml` version entries and library coordinates.
- `spring-platform/build.gradle.kts` and `spring-cloud-platform/build.gradle.kts` constraints.
- `service-core/build.gradle.kts` and `service-web/build.gradle.kts` dependency declarations.
- Root `build.gradle.kts` Javadoc external links.
- New `SensitiveDataSerializationTest` in `service-core` for the pre-migration masking baseline.
- Dependency documentation and the initial consumer migration notes.

### Non-goals

- Production Java changes. Both modules are expected not to compile after the dependency switch.
- Workflow or Renovate changes.

### Required context

- This plan's Target Dependency Set and Gotchas sections.
- `gradle/libs.versions.toml`, `spring-platform/build.gradle.kts`,
  `spring-cloud-platform/build.gradle.kts`, `build.gradle.kts`.
- Both runtime module build scripts and `service-core` logging sources/tests.
- [docs/dependency-automation.md](../dependency-automation.md),
  [docs/testing-patterns.md](../testing-patterns.md),
  [docs/versioning-and-compatibility.md](../versioning-and-compatibility.md).

### Execution steps

1. Confirm `build.gradle.kts` declares `version = "0.0.18-SNAPSHOT"`, and leave it unchanged.
   Run the existing clean build before changing dependencies. Stop on baseline failures.
2. Add and run `SensitiveDataSerializationTest` against the current Jackson 2 implementation.
   Exercise `SafeLogger.toJson()` with real objects containing field/getter `@Sensitive`
   annotations, full masking, `showLast` and custom mask characters, short/null values, and nested
   objects. Assert both masked output and preserved ordinary fields so a fallback `{}` cannot
   pass. Check that secret portions are absent. Do not rewrite existing assertions.
3. In `gradle/libs.versions.toml`, set `springBoot = "4.1.2"`, `springCloud = "2025.1.4"`,
   `springModulith = "2.1.2"`, `springdoc = "3.1.1"`, and `shedlock = "7.10.1"`. Remove the
   `testcontainers` version entry.
4. Update library coordinates and every affected build-script reference together:

   - Add `spring-boot-starter-webmvc` and point existing usages at it, replacing the deprecated
     `spring-boot-starter-web` entry.
   - Add test starters `spring-boot-starter-webmvc-test`, `spring-boot-starter-webflux-test`,
     `spring-boot-starter-security-test`, and `spring-boot-starter-data-jpa-test` (versions come
     from the Boot BOM).
   - Change `jackson-databind` to `tools.jackson.core:jackson-databind` and remove
     `jackson-datatype-jsr310`. Add `jackson-annotations` (`com.fasterxml.jackson.core:jackson-annotations`)
     only if a module compiles against annotations without getting them transitively.
   - Remove `libs.jackson.datatype.jsr310` from `service-core/build.gradle.kts` in this phase.
     Replace all `libs.spring.boot.starter.web` usages with the new webmvc alias, including tests.
   - In `service-core`, replace the generic test starter with the data-jpa and security test
     starters. In `service-web`, use the webmvc, webflux, security, and data-jpa test starters.
     Remove redundant test declarations only where a replacement starter supplies them;
     preserve required Reactor test and JUnit launcher support.
   - Add `springdoc-openapi-webflux` (`org.springdoc:springdoc-openapi-starter-webflux-ui`) for
     Phase 4's isolated reactive smoke tests. Do not add both Springdoc web integrations to the
     same runtime configuration.
   - Preserve main-source `api`, `implementation`, and stack/JPA `compileOnly` boundaries.
5. In `spring-platform/build.gradle.kts`, delete the four Testcontainers constraints and their
   Docker 29 `because(...)` text, because Boot's imported `testcontainers-bom` now manages
   Testcontainers 2.0.5. Keep the Springdoc, WireMock, Awaitility, and ShedLock constraints.
6. Update the Javadoc `links(...)` in root `build.gradle.kts` to Spring Framework 7.0.10
   (`https://docs.spring.io/spring-framework/docs/7.0.10/javadoc-api/`) and Jakarta EE 11
   (`https://jakarta.ee/specifications/platform/11/apidocs/`). Keep the Java 25 link.
7. Update starter and Testcontainers coordinate examples in affected docs. Create the consumer
   migration notes with the target versions, snapshot exception, baseline results, dependency
   changes, and the later consumer-integration/release gate.

### Implementation notes

- Removing the Testcontainers constraints is a platform-breaking change for consumers that declare
  the old `org.testcontainers:postgresql` or `junit-jupiter` names. Those names have no 2.x
  release. Record this in the consumer migration notes now.
- Do not add version overrides on top of the Boot BOM.
- Run the masking baseline before switching dependencies; the old Java sources cannot compile
  against Jackson 3 until Phase 2. Do not temporarily revert dependencies in later phases.

### Validation

Before the dependency switch:

```bash
./gradlew clean build --warning-mode all
# After adding the regression tests:
./gradlew clean spotlessApply
./gradlew :service-core:build --warning-mode all
```

After the dependency switch:

```bash
./gradlew :spring-platform:generatePomFileForMavenJavaPublication \
  :spring-cloud-platform:generatePomFileForMavenJavaPublication
cat spring-platform/build/publications/mavenJava/pom-default.xml
cat spring-cloud-platform/build/publications/mavenJava/pom-default.xml
./gradlew :service-core:dependencyInsight --configuration compileClasspath \
  --dependency spring-boot
./gradlew :service-web:dependencyInsight --configuration testRuntimeClasspath \
  --dependency spring-security-core
./gradlew :service-core:dependencies --configuration testRuntimeClasspath
./gradlew :service-web:dependencies --configuration testRuntimeClasspath
```

The base POM must import Boot 4.1.2 and Modulith 2.1.2. The Cloud POM must import this project's
base platform and Cloud 2025.1.4. Neither may contain the removed Testcontainers constraints.
Dependency reports must contain no unresolved/`FAILED` dependencies; resolve Boot 4.1.2,
Security 7.1.2, Jackson 3.1.7, and the new test starters. Reports alone may exit successfully with
unresolved dependencies, so inspect their contents.

### Completion criteria

The original build and new masking regression tests passed before the switch. Gradle configures
all projects, platform POMs generate correctly, and dependency resolution succeeds after the
switch. Main/test source migration remains for Phases 2 and 3. Dependency docs and durable
migration notes are updated.

## Phase 2: Service-Core Migration

### Workspace

.

### Goal

Make `service-core` build, test, and meet coverage on Boot 4.1.2 and Jackson 3 with unchanged
logging and masking behavior.

### Scope

- Minimal `service-core/build.gradle.kts` adjustments only if relocated types need explicit modules.
- Jackson 3 migration of `SafeLogger`, `SensitiveDataModule`, `UnknownTypeSerializer`, and
  `HttpBodyLogSanitizer` in `service-core/src/main/java/org/budgetanalyzer/core/logging/`.
- `PrometheusEndpointPostProcessor` and `ApplicationMetricTagPostProcessor`: relocate the
  `EnvironmentPostProcessor` import and update `META-INF/spring.factories`.
- `ServiceCoreJpaAutoConfiguration`: relocated `DataSourceAutoConfiguration` import.
- `service-core` test imports, Jackson construction, and focused regression coverage.
- Core API/configuration documentation and consumer migration notes.

### Non-goals

- `service-web` changes.
- New features or refactoring beyond what the migration requires.

### Required context

- This plan's Gotchas section.
- [docs/testing-patterns.md](../testing-patterns.md).
- `service-core/src/main/java/org/budgetanalyzer/core/logging/`,
  `service-core/src/main/java/org/budgetanalyzer/core/config/`,
  `service-core/src/main/resources/META-INF/`.
- Phase 1's masking tests and `docs/plans/spring-boot-4-consumer-migration.md`.

### Execution steps

1. Read the baseline masking tests before rewriting serializers. Keep their behavior assertions
   unchanged, adding focused cases only where the migration exposes a gap. Dependency and test
   starter wiring is already complete from Phase 1.
2. Migrate `SafeLogger` to an immutable `JsonMapper.builder()` configuration that preserves the
   current behavior: indented output, ISO dates rather than timestamps, no failure on empty beans,
   `SensitiveDataModule`, and the toString fallback module. Remove `JavaTimeModule` registration.
3. Migrate `SensitiveDataModule` and `UnknownTypeSerializer` to `ValueSerializerModifier`,
   `ValueSerializer`, and `SerializationContext`, the `BeanDescription.Supplier` callback
   signatures/property introspection, and the renamed `JsonGenerator` methods.
   Their public class names and public constructors stay the same.
4. Migrate `HttpBodyLogSanitizer` to `tools.jackson` tree types (`StringNode` replaces `TextNode`).
   Keep its existing failure fallback behavior on unparsable bodies; `JacksonException` is now
   unchecked.
5. Change both post-processors to implement `org.springframework.boot.EnvironmentPostProcessor`, and
   change the `spring.factories` key to `org.springframework.boot.EnvironmentPostProcessor`.
6. Update `ServiceCoreJpaAutoConfiguration` to import
   `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`. Relocate
   `EntityScan` to `org.springframework.boot.persistence.autoconfigure` in the core test
   application that actually uses it. Add an explicit `compileOnly` module only if the data-jpa
   starter does not already expose a required main-source type.
7. Fix remaining test imports and Jackson construction. Run logging tests first, then the full
   core build, including JPA auditing and soft-delete tests.
8. Update affected core documentation and record changed public Jackson supertypes, serializer
   methods, and post-processor interfaces in the consumer migration notes.

### Implementation notes

- Jackson 3 sorts properties alphabetically by default. If `SafeLogger` tests assert declaration
  order, preserve declaration order in the mapper configuration rather than editing the tests.
- Check whether Hibernate 7 or `spring.jpa.database-platform: org.hibernate.dialect.H2Dialect` in
  `service-core/src/test/resources/application.yml` logs deprecation warnings. Remove the explicit
  dialect only if Hibernate reports it as redundant.

### Validation

```bash
./gradlew clean spotlessApply
./gradlew :service-core:test --tests 'org.budgetanalyzer.core.logging.*'
./gradlew :service-core:build --warning-mode all
```

`service-core` must pass tests, Checkstyle, Spotless, Javadoc, and its JaCoCo minimums (80% line,
70% branch).

### Completion criteria

`service-core` builds green on Boot 4.1.2, including the unchanged baseline masking assertions.
No Jackson 2 core/databind/datatype references remain in its sources; Jackson annotations remain
valid. Affected docs and public API changes are recorded in the consumer migration notes.

## Phase 3: Service-Web Migration And Existing Tests

### Workspace

.

### Goal

Make `service-web` main and test sources work against Boot 4.1.2, Spring Security 7, Springdoc 3,
and Jackson 3, with the existing suite and full repository build passing.

### Scope

- Minimal `service-web/build.gradle.kts` dependency adjustments if compilation exposes a gap.
- Relocated Boot imports in `ServiceWebAutoConfiguration`, `ReactiveErrorWebExceptionHandler`, and
  `ClaimsHeaderSecurityConfig`.
- Jackson 3 migration of `ClaimsHeaderSecurityConfig`, `ReactiveClaimsHeaderSecurityConfig`,
  `ClaimsHeaderAuthenticationFilter`, `ClaimsHeaderSecurityErrorResponseWriter`,
  `ReactiveClaimsHeaderSecurityErrorResponseWriter`, `ReactiveErrorWebExceptionHandler`, and
  `ServiceWebAutoConfiguration`.
- `BaseOpenApiConfig` and other Springdoc customizer usage for Springdoc 3.
- Published test-support classes under `org.budgetanalyzer.service.security.test`.
- All existing web test sources, fixtures, and configuration.
- Web API/security/testing documentation and consumer migration notes.

### Non-goals

- New isolated consumer smoke tests and end-to-end OpenAPI coverage (Phase 4).
- Changing security rules, public paths, filter ordering, or error response shapes.

### Required context

- This plan's Gotchas section.
- [docs/error-handling.md](../error-handling.md), [docs/testing-patterns.md](../testing-patterns.md).
- `service-web/src/main/java/org/budgetanalyzer/service/` and `service-web/src/test/`.
- `docs/plans/spring-boot-4-consumer-migration.md`.

### Execution steps

1. Confirm Phase 1's main/test starter wiring is in place. Keep stack-specific `compileOnly`
   boundaries; add explicit modules only for demonstrated missing types.
2. Relocate imports: `ErrorWebFluxAutoConfiguration`, `ErrorWebExceptionHandler`, and
   `SecurityProperties` (see Gotchas).
3. Change injected and constructor `ObjectMapper` parameters to
   `tools.jackson.databind.json.JsonMapper`, which is the bean type Boot 4 auto-configures. Using
   `JsonMapper` rather than `ObjectMapper` also avoids ambiguity when an `XmlMapper` bean is
   present. Update Javadoc `@param` text to match.
4. Update the error-response writers for Jackson 3's unchecked exceptions without changing
   response status, content type, or body.
5. Fix compile issues in `BaseOpenApiConfig` and Springdoc customizers. Preserve the
   documented standard-response and `@PreAuthorize` 401/403 behavior.
6. Confirm `ClaimsHeaderTestBuilder` and `TestClaimsSecurityConfig` compile against Spring
   Security Test 7 and Spring Test 7.
7. Migrate all existing test imports and fixtures in this phase: `AutoConfigureMockMvc`,
   `AutoConfigureWebTestClient`, `JacksonAutoConfiguration`, `SecurityAutoConfiguration`,
   `WebMvcAutoConfiguration`, `DispatcherServletAutoConfiguration`, `WebFluxAutoConfiguration`,
   `ErrorWebExceptionHandler`, `DataSourceAutoConfiguration`, and `EntityScan` where used.
   Replace Jackson 2 mapper construction and `withBean(ObjectMapper.class, ObjectMapper::new)`
   with Jackson 3 `JsonMapper` equivalents; adapt removed exception APIs as necessary.
8. Run focused security, exception-handler, configuration, and JSON-model tests, then the full
   build. Resolve startup/wiring and serialization failures now. Preserve API response shapes,
   contractually required JSON order, and application mapper customization, including the
   existing `spring.jackson.serialization.indent_output` test property.
9. Update affected web/testing documentation and append all public signature changes and consumer
   test migration actions to the migration notes.

### Implementation notes

- `ApiErrorResponse` and `PagedResponse` use `com.fasterxml.jackson.annotation` types, which remain
  valid in Jackson 3.
- Gradle compiles the whole test source set before running selected tests. Complete the mechanical
  test migration before relying on test filters for focused debugging.
- Preserve existing security rules, default-chain backoff, and reactive method-security behavior.
- Remove `spring.main.allow-bean-definition-overriding` from tests only if it is demonstrably unused.

### Validation

```bash
./gradlew clean spotlessApply
./gradlew :service-web:test --tests '*Security*' --tests '*ExceptionHandler*' \
  --tests '*ConfigTest' --tests '*ResponseTest'
./gradlew clean build --warning-mode all
```

### Completion criteria

The existing servlet and reactive tests and the full build pass, including Javadoc, formatting,
Checkstyle, and unchanged JaCoCo minimums (`service-web`: 93% line, 80% branch). No Jackson 2
core/databind/datatype references remain in main or test sources. No startup or behavior failures
are deferred to Phase 4. Affected docs and public API migration notes are current.

## Phase 4: Consumer Classpaths And Runtime Behavior

### Workspace

.

### Goal

Verify the migrated library on its supported consumer classpaths and through real HTTP/OpenAPI
integration, closing the gaps that the existing combined-classpath suite cannot cover.

### Scope

- Focused core classpath-absence tests and separate servlet/reactive smoke test configurations.
- Test fixtures and build wiring in this repository only.
- Minimal production corrections required by those tests.
- Runtime behavior documentation and consumer migration notes.

### Non-goals

- Consumer repository execution or edits, new application features, and a general test framework.
- Reworking the existing combined-classpath suite or duplicating all its test cases.

### Required context

- [docs/testing-patterns.md](../testing-patterns.md).
- This plan's Gotchas section.
- Both runtime module build scripts, auto-configuration imports, and existing integration tests.
- `BaseOpenApiConfig`, both security configurations, and both environment post-processors.
- `docs/plans/spring-boot-4-consumer-migration.md`.

### Execution steps

1. Add a core-only context test with web, JPA, and security classes absent. Use an
   `ApplicationContextRunner` with `FilteredClassLoader` for this focused absence check. Confirm
   core utilities/autoconfiguration work and JPA auditing configuration is not activated.
2. Add two small, separate Gradle smoke test suites/tasks in `service-web`: `servletSmokeTest`
   and `reactiveSmokeTest`. Each consumes the main library and declares only its own stack's
   starter, test support, security, and Springdoc integration. Do not inherit the existing
   combined `testRuntimeClasspath`. Use minimal fixtures without JPA and wire both tasks into
   `check`; leave the existing suite in place.
3. Confirm each resolved runtime graph and startup mode: servlet has no WebFlux/Netty or reactive
   Springdoc; reactive has no servlet/Tomcat, MVC Springdoc, or JPA/Hibernate. Merely setting
   `spring.main.web-application-type` is not classpath isolation. Start both through Boot's
   normal auto-configuration discovery, without scanning library packages or supplying a mapper
   bean that would conceal missing Jackson auto-configuration.
4. In both smoke suites, use real application beans and HTTP requests to verify:

   - Startup and a representative successful response, plus the standard unauthorized (401),
     forbidden (403), and exception `ApiErrorResponse` contracts.
   - Default security chains back off for a consumer-defined chain; reactive `@PreAuthorize`
     still enforces permissions. Reuse existing assertions/fixture patterns where practical.
   - `/actuator/health/liveness`, `/actuator/health/readiness`, and `/actuator/prometheus` are
     accessible without claims. A representative protected application endpoint stays protected.
     Verify a metrics sample has the configured application tag, exercising `spring.factories`
     discovery of both post-processors rather than calling them directly.
   - An actual `/v3/api-docs` request succeeds using a fixture configuration extending
     `BaseOpenApiConfig`. Assert the standard method-specific responses and error schema,
     `@PreAuthorize` 401/403 responses, and preservation of an explicit response. Run each with
     its matching Springdoc integration; manually constructing an `OpenAPI` model is insufficient.
5. Fix migration regressions found by these checks without weakening existing assertions or
   making optional stack/JPA dependencies transitive. Update the affected docs and migration notes.
6. Run the full build with warning reporting. Inspect compilation output from that build and fix
   repository-source deprecations, or record why a warning is acceptable. Investigate generic
   compiler deprecation notices with a focused `-Xlint:deprecation` diagnostic if necessary.

### Implementation notes

- Use focused fixtures for library behavior, not copies of real consumer applications. Adding
  source sets/test tasks here must not add production dependencies or published artifacts.
- The root build currently configures coverage for the existing `test` task. Keep that reporting
  and its minimums intact; new smoke tasks supplement it and must run under `check`.
- If Boot 4 changes required consumer-visible behavior, record the conflict and stop; do not mark
  the phase complete merely because the difference has been documented.

### Validation

```bash
./gradlew clean spotlessApply
./gradlew :service-core:test --tests '*Classpath*'
./gradlew :service-web:servletSmokeTest :service-web:reactiveSmokeTest
./gradlew clean build --warning-mode all
```

Name the core absence test with a `ClasspathTest` suffix so the focused command selects it.
The final build must execute both smoke tasks through `check`. Review warnings from this actual
clean build; do not rerun an up-to-date build through `grep` or suppress the build's exit status.

### Completion criteria

Core starts without optional technologies. Both isolated web suites pass the HTTP, security,
actuator, and generated OpenAPI checks. The full build passes with unchanged coverage gates and
no unexplained repository-source deprecation warnings. There are no unresolved behavior changes;
documentation and migration notes contain the validation results and any accepted limitations.

## Phase 5: Documentation Review And Local Publication

### Workspace

.

### Goal

Review the documentation accumulated during implementation, then publish and inspect all four
development artifacts for the later consumer integration plan.

### Scope

- Final review of `AGENTS.md`, `README.md`, and affected `docs/*.md` examples.
- Finalization of `docs/plans/spring-boot-4-consumer-migration.md`, maintained since Phase 1.
- Local publication and inspection of all four artifacts and their metadata.

### Non-goals

- Consumer repository execution or edits, remote publication, stable release, or deployment.
- Version-specific statements in canonical docs. Release-specific details go only in
  `docs/plans/`.

### Required context

- AGENTS.md Documentation Discipline and Documentation Strategy sections.
- [AGENTS.md checkstyle](../../../orchestration/docs/agents-md-checkstyle.md). Read it before
  editing `AGENTS.md`.
- [docs/testing-patterns.md](../testing-patterns.md), [docs/advanced-patterns.md](../advanced-patterns.md),
  [docs/common-patterns.md](../common-patterns.md), [docs/code-quality-standards.md](../code-quality-standards.md).
- `docs/plans/spring-boot-4-consumer-migration.md` and the validation results from Phases 1–4.
- [docs/versioning-and-compatibility.md](../versioning-and-compatibility.md).

### Execution steps

1. Review the docs updated in earlier phases for missed starter names, Testcontainers coordinates
   and imports, test annotations, and Jackson types. Preserve the prohibition on mocking or spying
   application-owned beans; do not introduce `@MockitoBean` examples. Keep canonical guidance
   version-agnostic and document how to run the new smoke tests.
2. Reconcile the migration notes with the final source/build diff. Cover target dependencies;
   starter/test annotation changes; Testcontainers artifact/package changes; Jackson construction,
   bean injection, serializers, and every changed public signature/supertype/interface; Springdoc;
   ShedLock; and Modulith. Distinguish absent Jackson 2 `ObjectMapper` beans from Jackson 3's
   supported `JsonMapper` bean.
3. Verify consumer guidance against read-only discovery. Currently `currency-service` imports
   `spring-cloud-platform` and uses Cloud Stream; `session-gateway` uses the base `spring-platform`.
   Assign Cloud migration actions to actual Cloud consumers. Include the consumer Boot plugin
   version update alongside the platform/runtime library coordinate update. Record that this
   iteration has not tested migrated real consumers and does not authorize a stable release.
4. Run the consumer-pin prerequisite from Versioning immediately before publication. Confirm the
   pin in each consumer's checked-in HEAD as well as its working tree, so an uncommitted local edit
   cannot stand in for the prerequisite. Stop if any discovered consumer does not pin `0.0.17`.
5. Reuse Phase 4's successful full-build evidence if no code/build/test changes followed it.
   If this review requires such changes, format and rerun the affected checks and full build
   before publishing. Run `publishToMavenLocal` only after both build and pin gates pass.
6. Inspect every local publication, not just the `service-web` directory. Both platform POMs must
   have the intended BOM/platform imports. Runtime POMs and Gradle module metadata must use the
   correct coordinated version and preserve optional stack/JPA boundaries. Confirm both runtime
   jars contain the expected auto-configuration imports and `service-core` contains the updated
   `spring.factories` key. Record the result in the migration notes.

### Implementation notes

- Do not duplicate the gotchas into multiple canonical docs. Link to the consumer migration plan
  from the one most relevant place if a link is needed.
- The consumer migration document is guidance for authoring a later executable integration plan;
  do not imply consumer builds or a stable release have already been validated.
- Local publication makes artifacts available but does not itself verify consumer builds. Preserve
  that distinction in the final handoff.

### Validation

```bash
rg -l 'org\.budgetanalyzer:(spring-platform|spring-cloud-platform|service-core|service-web)' \
  ../*/gradle/libs.versions.toml
rg -n '^serviceCommon\s*=' ../*/gradle/libs.versions.toml
# Verify each discovered consumer's HEAD pin as described above before proceeding.
# Publish only after the build and pin gates pass:
./gradlew publishToMavenLocal
rg -n 'com\.fasterxml\.jackson\.(core|databind|datatype)' service-*/src
```

The final scan must have no matches (ripgrep exit code 1 means no matches; errors are not success).
Discover the Maven Local repository from the environment/Maven settings, then inspect all four
artifact directories at `org/budgetanalyzer/<artifact>/0.0.18-SNAPSHOT/`. Runtime publications must
include binary, sources, and Javadoc jars plus POM/module metadata; platform publications must
include POM/module metadata and do not have runtime jars.

### Completion criteria

The full build and isolated smoke checks passed for the final code. Every discovered consumer
pins `0.0.17`; all four artifacts are published locally as `0.0.18-SNAPSHOT` with inspected metadata
and runtime registration resources. Source scans and documentation review pass. The migration
notes contain the complete consumer/API changes, validation evidence, and outstanding consumer
integration/stable-release gates. No remote publish or consumer migration has occurred.
