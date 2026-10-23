# Spring Boot 4 Upgrade Plan

Upgrade `service-common` from Spring Boot 3.5.16 to the latest compatible Spring Boot 4 dependency
set, resolving the major-migration items on the Renovate Dependency Dashboard (issue #56). This
first iteration changes only this repository. A later integration plan moves the consumers onto it.

## Status

Draft for iteration.

## Versioning

- The version literal in `build.gradle.kts` stays `0.0.18-SNAPSHOT`. That snapshot line becomes
  the Spring Boot 4 line. Do not change the version in any phase.
- Consumer services are being reverted to the published `0.0.17` release from GitHub Packages, so
  replacing `0.0.18-SNAPSHOT` with Boot 4 content does not affect them. This applies both to Maven
  Local (`publishToMavenLocal`) and to GitHub Packages (the snapshot workflow on `main` merges).
- Prerequisite before any publish (Phase 5): every consumer must already pin `0.0.17`. Check it
  read-only with `grep -n "serviceCommon" ../*/gradle/libs.versions.toml`. If any consumer still
  pins `0.0.18-SNAPSHOT`, stop and tell the user. Do not edit consumer repositories.

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
  `JsonGenerator.writeFieldName` -> `writeName`. `JavaTimeModule` is built in, so
  `jackson-datatype-jsr310` goes away. Mappers are immutable and built with `JsonMapper.builder()`.
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
  exists in 7.1.2. Watch for any deprecation warnings it produces.
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
- This is an intentionally breaking release line. Public constructor and method signatures that
  take a Jackson 2 `ObjectMapper` change to Jackson 3 types. Record every public API change in the
  consumer migration notes (Phase 5) instead of adding compatibility shims.
- Do not modify, weaken, or delete existing tests to make them pass. Adapt imports and Jackson
  construction where the old API no longer exists. If an assertion fails because behavior changed,
  fix the production code to preserve behavior, or stop and record the decision.
- Keep the JaCoCo minimums in the root `build.gradle.kts` unchanged.
- Pure JPA only. No `org.hibernate.*` imports.
- No git write operations.

## Phase 1: Dependency Platform And Catalog

### Workspace

.

### Goal

Move the version catalog and both platform modules to the target dependency set, with the
published platform POMs resolving the Boot 4.1.2, Cloud 2025.1.4, and Modulith 2.1.2 BOMs.

### Scope

- `gradle/libs.versions.toml` version entries and library coordinates.
- `spring-platform/build.gradle.kts` and `spring-cloud-platform/build.gradle.kts` constraints.
- Root `build.gradle.kts` Javadoc external links.

### Non-goals

- Java source changes. `service-core` and `service-web` are expected not to compile at the end of
  this phase.
- Workflow or Renovate changes.

### Required context

- This plan's Target Dependency Set and Gotchas sections.
- `gradle/libs.versions.toml`, `spring-platform/build.gradle.kts`,
  `spring-cloud-platform/build.gradle.kts`, `build.gradle.kts`.
- [docs/dependency-automation.md](../dependency-automation.md).

### Execution steps

1. Confirm `build.gradle.kts` still declares `version = "0.0.18-SNAPSHOT"`, and leave it unchanged.
2. In `gradle/libs.versions.toml`, set `springBoot = "4.1.2"`, `springCloud = "2025.1.4"`,
   `springModulith = "2.1.2"`, `springdoc = "3.1.1"`, and `shedlock = "7.10.1"`. Remove the
   `testcontainers` version entry.
3. Update library coordinates in the catalog:
   - Add `spring-boot-starter-webmvc` and point existing usages at it, replacing the deprecated
     `spring-boot-starter-web` entry.
   - Add test starters `spring-boot-starter-webmvc-test`, `spring-boot-starter-webflux-test`,
     `spring-boot-starter-security-test`, and `spring-boot-starter-data-jpa-test` (versions come
     from the Boot BOM).
   - Change `jackson-databind` to `tools.jackson.core:jackson-databind` and remove
     `jackson-datatype-jsr310`. Add `jackson-annotations` (`com.fasterxml.jackson.core:jackson-annotations`)
     only if a module compiles against annotations without getting them transitively.
   - Add `springdoc-openapi-webflux` (`org.springdoc:springdoc-openapi-starter-webflux-ui`) only
     if Phase 3 needs it for reactive compile or test coverage.
4. In `spring-platform/build.gradle.kts`, delete the four Testcontainers constraints and their
   Docker 29 `because(...)` text, because Boot's imported `testcontainers-bom` now manages
   Testcontainers 2.0.5. Keep the Springdoc, WireMock, Awaitility, and ShedLock constraints.
5. Update the Javadoc `links(...)` in root `build.gradle.kts` to Spring Framework 7.0.10
   (`https://docs.spring.io/spring-framework/docs/7.0.10/javadoc-api/`) and Jakarta EE 11
   (`https://jakarta.ee/specifications/platform/11/apidocs/`). Keep the Java 25 link.

### Implementation notes

- Removing the Testcontainers constraints is a platform-breaking change for consumers that declare
  the old `org.testcontainers:postgresql` or `junit-jupiter` names. Those names have no 2.x
  release. Record this for the Phase 5 consumer migration notes.
- Do not add version overrides on top of the Boot BOM.

### Validation

```bash
./gradlew :spring-platform:generatePomFileForMavenJavaPublication \
  :spring-cloud-platform:generatePomFileForMavenJavaPublication
cat spring-platform/build/publications/mavenJava/pom-default.xml
cat spring-cloud-platform/build/publications/mavenJava/pom-default.xml
./gradlew :service-core:dependencyInsight --configuration compileClasspath \
  --dependency spring-boot
./gradlew :service-web:dependencyInsight --configuration testRuntimeClasspath \
  --dependency spring-security-core
```

The POMs must import `spring-boot-dependencies:4.1.2`, `spring-modulith-bom:2.1.2`, and (cloud only)
`spring-cloud-dependencies:2025.1.4`, with no Testcontainers constraints. Dependency insight must
resolve Boot 4.1.2 and Security 7.1.x.

### Completion criteria

The catalog and platforms declare the target set. Platform POMs generate correctly. The only
remaining build failures are Java compilation errors in `service-core` and `service-web`.

## Phase 2: Service-Core Migration

### Workspace

.

### Goal

Make `service-core` build, test, and meet coverage on Boot 4.1.2 and Jackson 3 with unchanged
logging and masking behavior.

### Scope

- `service-core/build.gradle.kts` dependency declarations.
- Jackson 3 migration of `SafeLogger`, `SensitiveDataModule`, `UnknownTypeSerializer`, and
  `HttpBodyLogSanitizer` in `service-core/src/main/java/org/budgetanalyzer/core/logging/`.
- `PrometheusEndpointPostProcessor` and `ApplicationMetricTagPostProcessor`: relocate the
  `EnvironmentPostProcessor` import and update `META-INF/spring.factories`.
- `ServiceCoreJpaAutoConfiguration`: relocated `DataSourceAutoConfiguration` and `EntityScan`
  imports.
- `service-core` tests: imports and Jackson construction only.

### Non-goals

- `service-web` changes.
- New features or refactoring beyond what the migration requires.

### Required context

- This plan's Gotchas section.
- [docs/testing-patterns.md](../testing-patterns.md).
- `service-core/src/main/java/org/budgetanalyzer/core/logging/`,
  `service-core/src/main/java/org/budgetanalyzer/core/config/`,
  `service-core/src/main/resources/META-INF/`.

### Execution steps

1. Update `service-core/build.gradle.kts`: use the Jackson 3 `jackson-databind` coordinate, drop
   `jackson-datatype-jsr310`, and replace `spring-boot-starter-test` with
   `spring-boot-starter-data-jpa-test` plus `spring-boot-starter-security-test` where the tests need
   them. Keep `compileOnly` boundaries unchanged.
2. Migrate `SafeLogger` to an immutable `JsonMapper.builder()` configuration that preserves the
   current behavior: indented output, ISO dates rather than timestamps, no failure on empty beans,
   `SensitiveDataModule`, and the toString fallback module. Remove `JavaTimeModule` registration.
3. Migrate `SensitiveDataModule` and `UnknownTypeSerializer` to `ValueSerializerModifier`,
   `ValueSerializer`, and `SerializationContext`, and to the renamed `JsonGenerator` methods.
   Their public class names and public constructors stay the same.
4. Migrate `HttpBodyLogSanitizer` to `tools.jackson` tree types (`StringNode` replaces `TextNode`).
   Keep its existing failure fallback behavior on unparsable bodies; `JacksonException` is now
   unchecked.
5. Change both post-processors to implement `org.springframework.boot.EnvironmentPostProcessor`, and
   change the `spring.factories` key to `org.springframework.boot.EnvironmentPostProcessor`.
6. Update `ServiceCoreJpaAutoConfiguration` imports to
   `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration` and
   `org.springframework.boot.persistence.autoconfigure.EntityScan`. Add an explicit `compileOnly`
   module only if the data-jpa starter does not already expose those types.
7. Fix test compilation: relocated imports, and `JsonMapper` instead of `new ObjectMapper()` where
   the Jackson 2 type is no longer present.

### Implementation notes

- Jackson 3 sorts properties alphabetically by default. If `SafeLogger` tests assert declaration
  order, preserve declaration order in the mapper configuration rather than editing the tests.
- Check whether Hibernate 7 or `spring.jpa.database-platform: org.hibernate.dialect.H2Dialect` in
  `service-core/src/test/resources/application.yml` logs deprecation warnings. Remove the explicit
  dialect only if Hibernate reports it as redundant.

### Validation

```bash
./gradlew clean spotlessApply
./gradlew :service-core:clean :service-core:build
```

`service-core` must pass tests, Checkstyle, Spotless, Javadoc, and its JaCoCo minimums (80% line,
70% branch).

### Completion criteria

`service-core` builds green on Boot 4.1.2. No `com.fasterxml.jackson.databind` or
`com.fasterxml.jackson.datatype` imports remain in `service-core`. Every public API change is
listed in a short note at the end of this phase's output for Phase 5.

## Phase 3: Service-Web Main Sources

### Workspace

.

### Goal

Make `service-web` main sources compile against Boot 4.1.2, Spring Security 7, Springdoc 3, and
Jackson 3 for both the servlet and reactive stacks.

### Scope

- `service-web/build.gradle.kts` main-source dependency declarations.
- Relocated Boot imports in `ServiceWebAutoConfiguration`, `ReactiveErrorWebExceptionHandler`, and
  `ClaimsHeaderSecurityConfig`.
- Jackson 3 migration of `ClaimsHeaderSecurityConfig`, `ReactiveClaimsHeaderSecurityConfig`,
  `ClaimsHeaderAuthenticationFilter`, `ClaimsHeaderSecurityErrorResponseWriter`,
  `ReactiveClaimsHeaderSecurityErrorResponseWriter`, `ReactiveErrorWebExceptionHandler`, and
  `ServiceWebAutoConfiguration`.
- `BaseOpenApiConfig` and other Springdoc customizer usage for Springdoc 3.
- Published test-support classes under `org.budgetanalyzer.service.security.test`.

### Non-goals

- Test sources (Phase 4).
- Changing security rules, public paths, filter ordering, or error response shapes.

### Required context

- This plan's Gotchas section.
- [docs/error-handling.md](../error-handling.md).
- `service-web/src/main/java/org/budgetanalyzer/service/`.

### Execution steps

1. Update `service-web/build.gradle.kts` `compileOnly` and `api` dependencies:
   `spring-boot-starter-webmvc` replaces `spring-boot-starter-web`, and Springdoc 3 is used. Keep
   the stack-specific `compileOnly` boundaries so reactive consumers do not inherit servlet
   dependencies.
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

### Implementation notes

- `ApiErrorResponse` and `PagedResponse` use `com.fasterxml.jackson.annotation` types, which remain
  valid in Jackson 3.
- If `@EnableReactiveMethodSecurity(useAuthorizationManager = true)` is deprecated, report it
  rather than removing the attribute silently.

### Validation

```bash
./gradlew clean spotlessApply
./gradlew :service-web:compileJava :service-web:javadoc
```

### Completion criteria

`service-web` main sources and Javadoc compile. No `com.fasterxml.jackson.databind` imports remain
in `service-web/src/main`. Public signature changes are listed for Phase 5.

## Phase 4: Service-Web Tests And Behavior Verification

### Workspace

.

### Goal

Get the full `service-web` test suite passing on Boot 4.1.2 with coverage minimums intact. Confirm
that consumer-visible behavior is unchanged.

### Scope

- `service-web/build.gradle.kts` test dependencies.
- `service-web/src/test/` import relocations and Jackson 3 construction.
- Behavior checks for JSON error responses, security chains, actuator exposure, and OpenAPI output.

### Non-goals

- Weakening, deleting, or rewriting assertions in existing tests.
- New features.

### Required context

- [docs/testing-patterns.md](../testing-patterns.md).
- This plan's Gotchas section.
- `service-web/src/test/java/org/budgetanalyzer/service/`.

### Execution steps

1. Replace `spring-boot-starter-test` and `spring-boot-starter-web` test dependencies with the Boot
   4 test starters (`spring-boot-starter-webmvc-test`, `spring-boot-starter-webflux-test`,
   `spring-boot-starter-security-test`, `spring-boot-starter-data-jpa-test`) and
   `spring-boot-starter-webmvc`. Keep `reactor-test`.
2. Relocate test imports: `AutoConfigureMockMvc`, `AutoConfigureWebTestClient`,
   `JacksonAutoConfiguration`, `SecurityAutoConfiguration`, `WebMvcAutoConfiguration`,
   `DispatcherServletAutoConfiguration`, `WebFluxAutoConfiguration`, and `DataSourceAutoConfiguration`.
3. Replace `withBean(ObjectMapper.class, ObjectMapper::new)` and `new ObjectMapper()` with Jackson 3
   `JsonMapper` equivalents.
4. Run the suite and resolve failures in production code. Pay particular attention to:
   - JSON field order in `ApiErrorResponse` and `PagedResponse` under Jackson 3 defaults. Keep the
     existing order with `@JsonPropertyOrder` or mapper configuration if tests or API contracts
     depend on it.
   - The `spring.jackson.serialization.indent_output` test property, which is still valid in Boot 4.
   - Default actuator liveness and readiness endpoints, which stay public under
     `/actuator/health/**` and nothing else.
   - The OpenAPI document produced through `BaseOpenApiConfig` on Springdoc 3.
5. Scan the build output for deprecation warnings that originate in this repository's sources and
   fix them.

### Implementation notes

- Remove the `spring.main.allow-bean-definition-overriding` test property only if it is unused.
  Otherwise leave it.
- If a test assertion encodes Boot 3 behavior that Boot 4 intentionally changed and the change is
  consumer-visible, stop and record the decision instead of editing the assertion.

### Validation

```bash
./gradlew clean spotlessApply
./gradlew clean build
./gradlew build --warning-mode all 2>&1 | grep -i deprecat || true
```

### Completion criteria

`./gradlew clean build` passes for all modules with the existing coverage minimums
(`service-web` 93% line, 80% branch). There are no unexplained deprecation warnings from this
repository's sources. Behavior differences found are fixed or recorded as decisions.

## Phase 5: Documentation, Consumer Migration Notes, And Final Validation

### Workspace

.

### Goal

Bring documentation in line with the Boot 4 baseline, record consumer migration guidance for the
later integration plan, and run the full validation and local publish.

### Scope

- `AGENTS.md`, `README.md`, and `docs/*.md` content that describes starters, Testcontainers,
  Jackson, test annotations, or Boot 3-era APIs.
- A new `docs/plans/spring-boot-4-consumer-migration.md` with release-specific guidance.

### Non-goals

- Editing consumer repositories.
- Version-specific statements in canonical docs. Release-specific details go only in
  `docs/plans/`.

### Required context

- AGENTS.md Documentation Discipline and Documentation Strategy sections.
- [AGENTS.md checkstyle](../../../orchestration/docs/agents-md-checkstyle.md). Read it before
  editing `AGENTS.md`.
- [docs/testing-patterns.md](../testing-patterns.md), [docs/advanced-patterns.md](../advanced-patterns.md),
  [docs/common-patterns.md](../common-patterns.md), [docs/code-quality-standards.md](../code-quality-standards.md).
- The public API change notes from Phases 2–4.

### Execution steps

1. Update the dependency examples in `AGENTS.md` and `docs/common-patterns.md` to use
   `spring-boot-starter-webmvc`. Update the `AGENTS.md` JSON-related descriptions that imply a
   Jackson 2 `ObjectMapper` bean.
2. Update `docs/testing-patterns.md` and `docs/advanced-patterns.md` examples for Testcontainers 2
   coordinates and packages, Boot 4 test starters, `@MockitoBean`, and Jackson 3 types. Keep the
   docs version-agnostic.
3. Write `docs/plans/spring-boot-4-consumer-migration.md`. It covers the target dependency set;
   consumer actions (starter renames, the Testcontainers artifact renames now that the platform
   constraints are gone, Jackson 3 migration and the absent `ObjectMapper` bean, test annotation
   relocations, Springdoc 3, Spring Cloud 2025.1 for session-gateway, ShedLock 7, Modulith 2.1);
   every public `service-common` API signature change; the move from `0.0.17` to the Boot 4
   `0.0.18-SNAPSHOT` line; and a consumer discovery command
   (`grep -l "service-common" ../*/build.gradle.kts`).
4. Run the read-only prerequisite check from the plan's Versioning section. Every consumer must
   pin `0.0.17`. If any consumer still pins `0.0.18-SNAPSHOT`, stop before publishing and report it.
5. Run the final validation and local publish below.

### Implementation notes

- Do not duplicate the gotchas into multiple canonical docs. Link to the consumer migration plan
  from the one most relevant place if a link is needed.

### Validation

```bash
./gradlew clean spotlessApply
./gradlew clean build
grep -n "serviceCommon" ../*/gradle/libs.versions.toml
./gradlew publishToMavenLocal
ls ~/.m2/repository/org/budgetanalyzer/service-web/0.0.18-SNAPSHOT/
grep -rn "com.fasterxml.jackson.databind\|com.fasterxml.jackson.datatype" service-*/src || true
```

### Completion criteria

The full build passes. Every consumer pins `0.0.17`, and all four artifacts are published to
Maven Local as `0.0.18-SNAPSHOT`. No Jackson 2 databind imports remain. The docs reflect the
Boot 4 baseline, and the consumer migration plan exists for the later integration iteration.
