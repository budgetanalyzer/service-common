# Plan: Spring Platform And Spring Cloud Platform

Date: 2026-05-05
Status: Complete

Related documents:

- `../versioning-and-compatibility.md`
- `../../README.md`
- `../../AGENTS.md`
- `/workspace/orchestration/docs/plans/dependency-upgrade-and-centralized-bom-plan.md`

## Context

`service-common` currently publishes shared runtime libraries:

- `org.budgetanalyzer:service-core`
- `org.budgetanalyzer:service-web`

Each backend service still carries its own version catalog for Spring Boot, selected Spring
ecosystem BOMs, SpringDoc, Testcontainers, WireMock, Awaitility, ShedLock, and other repeated JVM
libraries. This creates avoidable drift, especially around Spring Cloud and around temporary
overrides such as the Testcontainers Docker compatibility override.

The desired direction is to add Gradle `java-platform` projects that publish Maven BOM-style
artifacts. The platform artifacts own version selection. Services still explicitly declare only the
dependencies they use.

## Decision

Create two published platform artifacts from `service-common`:

```text
org.budgetanalyzer:spring-platform:<service-common-version>
org.budgetanalyzer:spring-cloud-platform:<service-common-version>
```

Use the same checked-in root `service-common` version for all four artifacts:

```text
spring-platform
spring-cloud-platform
service-core
service-web
```

`spring-platform` is the base backend Spring application platform. It imports the Spring Boot BOM
and the Spring Modulith BOM, then adds project-level constraints for JVM libraries used by Budget
Analyzer Spring services.

`spring-cloud-platform` is an opt-in overlay. It imports `spring-platform` and the Spring Cloud BOM.
Only services that use Spring Cloud APIs should import it.

## Goals

- Centralize backend Spring dependency version selection in `service-common`.
- Keep Spring Cloud centralized without adding Spring Cloud dependencies to non-Cloud services.
- Prepare for a future Spring Boot 4.x migration by reducing upgrade surfaces to one platform
  module plus the unavoidable Gradle plugin and source compatibility changes.
- Keep service dependency declarations explicit.
- Keep `service-web` autoconfiguration behavior unchanged.
- Preserve the existing lockstep service-common release model.

## Non-Goals

- Do not create a custom dependency resolution engine.
- Do not add a Gradle convention plugin in this first pass.
- Do not move frontend, Kubernetes, Tilt, Kind, image, or shell tool versions into these Maven
  platforms.
- Do not make Spring Cloud globally active for every service.
- Do not rely on `service-web` transitively smuggling dependency management into consumers.
- Do not change runtime autoconfiguration, security filters, exception handling, or HTTP logging.

## Target Module Layout

```text
service-common/
  settings.gradle.kts
  build.gradle.kts
  gradle/libs.versions.toml
  spring-platform/
    build.gradle.kts
  spring-cloud-platform/
    build.gradle.kts
  service-core/
    build.gradle.kts
  service-web/
    build.gradle.kts
```

`spring-platform` and `spring-cloud-platform` must not contain application code. They should only
publish dependency constraints and imported BOMs.

## Platform Contents

### `spring-platform`

Initial imported BOMs:

```kotlin
api(platform("org.springframework.boot:spring-boot-dependencies:<springBoot>"))
api(platform("org.springframework.modulith:spring-modulith-bom:<springModulith>"))
```

Initial constraints:

```text
org.springdoc:springdoc-openapi-starter-webmvc-ui
org.springdoc:springdoc-openapi-starter-webflux-ui
org.testcontainers:testcontainers
org.testcontainers:junit-jupiter
org.testcontainers:postgresql
org.testcontainers:rabbitmq
org.wiremock:wiremock-standalone
org.awaitility:awaitility
net.javacrumbs.shedlock:shedlock-spring
net.javacrumbs.shedlock:shedlock-provider-jdbc-template
```

Add `org.junit.platform:junit-platform-launcher` only if dependency insight shows the selected
version is not already managed correctly by the Spring Boot BOM.

Do not add service-specific libraries such as `org.apache.pdfbox:pdfbox` unless at least two
backend services use them and the version should intentionally be platform-owned.

## Version Selection Rule

The first implementation should centralize the versions already selected in the workspace. Do not
combine this migration with Spring Boot, Spring Cloud, or third-party patch upgrades.

Initial platform versions should come from the current service-common and consumer catalogs:

```text
springBoot = 3.5.7
springCloud = 2025.0.0
springModulith = 1.4.0
springdoc = 2.8.13
testcontainers = 1.21.4
wiremock = 3.10.0
awaitility = 4.2.2
shedlock = 6.0.2
```

After all services build against the new platforms with no intended version movement, apply patch
upgrades as a separate change by editing platform versions and the still-local Spring Boot Gradle
plugin version.

### `spring-cloud-platform`

Imported platforms:

```kotlin
api(platform(project(":spring-platform")))
api(platform("org.springframework.cloud:spring-cloud-dependencies:<springCloud>"))
```

Do not add Spring Cloud starter or binder dependencies as normal dependencies. This module should
only provide version management. Cloud usage remains service-owned and explicit.

## Service-Common Implementation

Current service-common status: implemented and locally validated. Consumer service changes were
completed in the same lockstep workspace update.

### 1. Update `settings.gradle.kts`

Add both platform modules:

```kotlin
include("spring-platform")
include("spring-cloud-platform")
include("service-core")
include("service-web")
```

Keep the root project name as `service-common`.

### 2. Update Root Build Wiring

The current root build applies `java-library`, Checkstyle, Spotless, Java toolchain, Javadoc, test
configuration, and `maven-publish` to every subproject. That is not valid for `java-platform`
modules.

Refactor `build.gradle.kts` into two groups:

```kotlin
val platformProjectNames = setOf("spring-platform", "spring-cloud-platform")

configure(subprojects.filter { it.name !in platformProjectNames }) {
    apply(plugin = "java-library")
    apply(plugin = "checkstyle")
    apply(plugin = "maven-publish")
    apply(plugin = "com.diffplug.spotless")

    // Existing Java, test, Javadoc, Spotless, Checkstyle, and Java-library publishing config.
}

configure(subprojects.filter { it.name in platformProjectNames }) {
    apply(plugin = "java-platform")
    apply(plugin = "maven-publish")

    // Platform publishing config using components["javaPlatform"].
}
```

Keep common `group`, `version`, repositories, GitHub Packages repository, and GitHub credential
guard shared.

For platform publications:

```kotlin
create<MavenPublication>("mavenJava") {
    from(components["javaPlatform"])
    groupId = project.group.toString()
    artifactId = project.name
    version = project.version.toString()
}
```

Do not call `withSourcesJar()`, `withJavadocJar()`, or Java source-set configuration for platform
projects.

### 3. Add `spring-platform/build.gradle.kts`

Use:

```kotlin
description = "Budget Analyzer Spring dependency platform"

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}"))
    api(platform("org.springframework.modulith:spring-modulith-bom:${libs.versions.springModulith.get()}"))

    constraints {
        // SpringDoc, Testcontainers, WireMock, Awaitility, ShedLock.
    }
}
```

Prefer version aliases in `gradle/libs.versions.toml` for platform-owned versions. Keep each
constraint close to the owning platform so the generated POM is the source of truth for consumers.

For the Testcontainers override, carry the rationale from the consumer builds into the platform:

```kotlin
because("Spring Boot 3.5.7 manages Testcontainers 1.21.3; 1.21.4 fixes Docker 29.x compatibility")
```

Update the rationale when the Spring Boot BOM eventually manages a compatible version and the
override can be removed.

### 4. Add `spring-cloud-platform/build.gradle.kts`

Use:

```kotlin
description = "Budget Analyzer Spring Cloud dependency platform"

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform(project(":spring-platform")))
    api(platform("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.springCloud.get()}"))
}
```

This keeps `currency-service` on a centrally managed Cloud release train without exposing Cloud
dependencies to services that do not use Cloud APIs.

### 5. Update `gradle/libs.versions.toml`

Add platform-owned versions that are not currently in the service-common catalog:

```toml
springCloud = "..."
springModulith = "..."
testcontainers = "..."
wiremock = "..."
awaitility = "..."
shedlock = "..."
```

Keep versions that are build-tool concerns and cannot be managed by a Maven platform:

```text
java
springBoot plugin version
spotless
checkstyle
googleJavaFormat
dependencyManagement, until removed everywhere
```

Keep service-specific versions out of the platform unless they become shared policy.

### 6. Update `service-core`

Remove the `io.spring.dependency-management` plugin and the local Spring Boot BOM import from
`service-core/build.gradle.kts`.

Add the base platform to dependencies:

```kotlin
dependencies {
    api(platform(project(":spring-platform")))

    api(libs.spring.boot.starter.actuator)
    api(libs.micrometer.registry.prometheus)
    // Existing dependencies stay explicit.
}
```

Keep JPA and Spring Security as `compileOnly` where they are currently compile-only. Do not make
them transitive.

### 7. Update `service-web`

Remove the `io.spring.dependency-management` plugin and the local Spring Boot BOM import from
`service-web/build.gradle.kts`.

Add the base platform to dependencies:

```kotlin
dependencies {
    api(platform(project(":spring-platform")))

    api(project(":service-core"))
    api(libs.commons.lang3)
    api(libs.spring.boot.starter.validation)
    // Existing compileOnly stack dependencies stay compileOnly.
}
```

Do not change servlet or reactive autoconfiguration. Do not move servlet, WebFlux, JPA, or security
starters from `compileOnly` to `api` or `implementation`.

## Consumer Service Changes

The current consumers are discoverable with:

```bash
grep -l "service-common\|org.budgetanalyzer:service-" /workspace/*/build.gradle.kts 2>/dev/null
```

Current expected consumers:

```text
/workspace/currency-service
/workspace/permission-service
/workspace/session-gateway
/workspace/transaction-service
```

### Shared Consumer Pattern

Each consumer keeps `service-web` or `service-core` explicit and imports exactly one platform:

```kotlin
dependencies {
    implementation(platform(libs.budgetanalyzer.spring.platform))
    implementation(libs.service.web)
}
```

Cloud consumers import the Cloud overlay instead:

```kotlin
dependencies {
    implementation(platform(libs.budgetanalyzer.spring.cloud.platform))
    implementation(libs.service.web)
}
```

Because `spring-cloud-platform` imports `spring-platform`, Cloud consumers do not need to import
both.

Add these aliases to each consumer catalog as appropriate:

```toml
[libraries]
budgetanalyzer-spring-platform = { module = "org.budgetanalyzer:spring-platform", version.ref = "serviceCommon" }
budgetanalyzer-spring-cloud-platform = { module = "org.budgetanalyzer:spring-cloud-platform", version.ref = "serviceCommon" }
```

Keep the service dependency aliases:

```toml
service-web = { module = "org.budgetanalyzer:service-web", version.ref = "serviceCommon" }
service-core = { module = "org.budgetanalyzer:service-core", version.ref = "serviceCommon" }
```

Remove the `io.spring.dependency-management` plugin from consumer builds once Gradle-native platform
imports are in place. Keep the Spring Boot Gradle plugin version in each catalog until a later
convention-plugin step centralizes Gradle plugin versions.

### `/workspace/currency-service`

Use `spring-cloud-platform` because this service currently uses Spring Cloud Stream.

Build file changes:

- Remove `extra["testcontainers.version"]`.
- Remove the `dependencyManagement` block that imports Spring Cloud and Spring Modulith BOMs.
- Remove `alias(libs.plugins.spring.dependency.management)`.
- Add `implementation(platform(libs.budgetanalyzer.spring.cloud.platform))`.
- Keep `implementation(libs.spring.cloud.stream)` and
  `implementation(libs.spring.cloud.stream.binder.rabbit)` explicit.
- Keep Spring Modulith dependencies explicit.
- Keep ShedLock dependencies explicit, but remove their local versions from the catalog.

Catalog changes:

- Add `budgetanalyzer-spring-cloud-platform`.
- Remove local version aliases now owned by the platform:
  `springCloud`, `springModulith`, `springdoc`, `shedlock`, `wiremock`, `awaitility`,
  `testcontainers`.
- Remove the `spring-dependency-management` plugin alias if no longer used.
- Remove explicit versions from Testcontainers, WireMock, Awaitility, ShedLock, SpringDoc, and
  Spring Modulith library aliases.
- Keep `springBoot` because it still controls the Spring Boot Gradle plugin.
- Keep `serviceCommon` because it controls all Budget Analyzer artifact versions.

Validation:

```bash
./gradlew clean build
./gradlew dependencyInsight --dependency spring-cloud-stream --configuration runtimeClasspath
./gradlew dependencyInsight --dependency spring-modulith --configuration runtimeClasspath
./gradlew dependencyInsight --dependency testcontainers --configuration testRuntimeClasspath
```

### `/workspace/transaction-service`

Use `spring-platform`.

Build file changes:

- Remove `extra["testcontainers.version"]`.
- Remove `alias(libs.plugins.spring.dependency.management)`.
- Add `implementation(platform(libs.budgetanalyzer.spring.platform))`.
- Keep `implementation(libs.service.web)` explicit.
- Keep `implementation(libs.pdfbox)` and its local version because it is service-specific.

Catalog changes:

- Add `budgetanalyzer-spring-platform`.
- Remove local version aliases now owned by the platform:
  `springdoc`, `testcontainers`.
- Remove the `spring-dependency-management` plugin alias if no longer used.
- Remove explicit versions from SpringDoc and Testcontainers library aliases.
- Keep `jacoco`, `pdfbox`, `springBoot`, `serviceCommon`, and build-tool versions.

Validation:

```bash
./gradlew clean build
./gradlew dependencyInsight --dependency springdoc-openapi --configuration runtimeClasspath
./gradlew dependencyInsight --dependency testcontainers --configuration testRuntimeClasspath
./gradlew dependencyInsight --dependency pdfbox --configuration runtimeClasspath
```

### `/workspace/permission-service`

Use `spring-platform`.

Build file changes:

- Remove `alias(libs.plugins.spring.dependency.management)`.
- Add `implementation(platform(libs.budgetanalyzer.spring.platform))`.
- Keep `implementation(libs.service.web)` explicit.

Catalog changes:

- Add `budgetanalyzer-spring-platform`.
- Remove local version aliases now owned by the platform:
  `springdoc`, `testcontainers`.
- Remove the `spring-dependency-management` plugin alias if no longer used.
- Remove explicit versions from SpringDoc and Testcontainers library aliases.
- Keep `springBoot`, `serviceCommon`, and build-tool versions.

Validation:

```bash
./gradlew clean build
./gradlew dependencyInsight --dependency springdoc-openapi --configuration runtimeClasspath
./gradlew dependencyInsight --dependency testcontainers --configuration testRuntimeClasspath
```

### `/workspace/session-gateway`

Use `spring-platform`. This service is reactive but is not currently a Spring Cloud consumer.

Build file changes:

- Remove `extra["testcontainers.version"]`.
- Remove `alias(libs.plugins.spring.dependency.management)`.
- Add `implementation(platform(libs.budgetanalyzer.spring.platform))`.
- Keep `implementation(libs.spring.boot.starter.webflux)` explicit.
- Keep the WebFlux SpringDoc module explicit.

Catalog changes:

- Add `budgetanalyzer-spring-platform`.
- Remove local version aliases now owned by the platform:
  `springdoc`, `wiremock`, `awaitility`, `testcontainers`.
- Remove the `spring-dependency-management` plugin alias if no longer used.
- Remove explicit versions from SpringDoc, WireMock, Awaitility, and Testcontainers library aliases.
- Keep `springBoot`, `serviceCommon`, and build-tool versions.

Validation:

```bash
./gradlew clean build
./gradlew dependencyInsight --dependency springdoc-openapi-starter-webflux-ui --configuration runtimeClasspath
./gradlew dependencyInsight --dependency testcontainers --configuration testRuntimeClasspath
./gradlew dependencyInsight --dependency wiremock --configuration testRuntimeClasspath
```

## Documentation Updates

Update documentation in the same implementation work:

### `README.md`

- Add `spring-platform` and `spring-cloud-platform` to the module overview.
- Add usage examples showing platform import plus explicit service dependencies.
- Clarify that `service-web` still provides autoconfiguration, while the platform artifacts only
  provide dependency version management.
- Update publishing text from "both modules" to "all service-common artifacts".

### `AGENTS.md`

- Update the module architecture section to list four modules.
- Add platform usage guidance under "Which Module to Depend On?".
- Add discovery commands for platform modules.
- Update publishing and consumption notes to include the two new coordinates.
- Keep the autoconfiguration section scoped to `service-core` and `service-web`.

### `docs/versioning-and-compatibility.md`

- Update the coordinated release model to include `spring-platform` and `spring-cloud-platform`.
- Add published coordinates:

  ```text
  org.budgetanalyzer:spring-platform:<service-common-version>
  org.budgetanalyzer:spring-cloud-platform:<service-common-version>
  org.budgetanalyzer:service-core:<service-common-version>
  org.budgetanalyzer:service-web:<service-common-version>
  ```

- Clarify that platform changes can be breaking when they remove constraints or move major
  framework versions.

### Consumer Docs

For each changed consumer service, update the nearest dependency or setup documentation if it lists
version ownership, service-common coordinates, or build conventions.

Do not update orchestration documentation from this repository. If the orchestration plan needs to
be revised after implementation, make that change from the orchestration repository under its own
write rules.

## Validation Plan

### Service-Common Validation

Run:

```bash
./gradlew clean spotlessApply
./gradlew clean build
./gradlew publishToMavenLocal
```

Inspect generated publication metadata:

```bash
cat spring-platform/build/publications/mavenJava/pom-default.xml
cat spring-cloud-platform/build/publications/mavenJava/pom-default.xml
```

Confirm:

- `spring-platform` imports the Spring Boot and Spring Modulith BOMs.
- `spring-platform` contains the intended constraints.
- `spring-cloud-platform` imports `spring-platform` and the Spring Cloud BOM.
- Neither platform artifact publishes Java classes, sources, or autoconfiguration metadata.
- `service-core` and `service-web` still publish normally.

### Consumer Validation

After publishing to Maven Local, run each backend build:

```bash
cd /workspace/currency-service && ./gradlew clean build
cd /workspace/transaction-service && ./gradlew clean build
cd /workspace/permission-service && ./gradlew clean build
cd /workspace/session-gateway && ./gradlew clean build
```

Run dependency drift checks:

```bash
rg -n "springCloud|springModulith|testcontainers.version|dependencyManagement" \
  /workspace/currency-service \
  /workspace/transaction-service \
  /workspace/permission-service \
  /workspace/session-gateway
```

Expected result:

- No consumer-local Spring Cloud version.
- No consumer-local Spring Modulith version.
- No consumer-local Testcontainers override.
- No consumer `dependencyManagement` block unless there is a documented temporary exception.

Run targeted dependency insight commands in the affected services:

```bash
./gradlew dependencyInsight --dependency spring-boot --configuration runtimeClasspath
./gradlew dependencyInsight --dependency spring-security --configuration runtimeClasspath
./gradlew dependencyInsight --dependency springdoc-openapi --configuration runtimeClasspath
./gradlew dependencyInsight --dependency testcontainers --configuration testRuntimeClasspath
```

For `currency-service`, also run:

```bash
./gradlew dependencyInsight --dependency spring-cloud-stream --configuration runtimeClasspath
./gradlew dependencyInsight --dependency spring-modulith --configuration runtimeClasspath
```

### Optional Local Runtime Validation

If this lands alongside dependency upgrades, run the local stack validation owned by orchestration.
For a pure platform refactor with no selected version changes, full service builds and dependency
insight are the minimum required validation.

## Rollout Order

1. Add `spring-platform` and `spring-cloud-platform` in `service-common`.
2. Refactor root publishing so platform and Java-library projects are configured separately.
3. Move `service-core` and `service-web` to consume `spring-platform`.
4. Publish `service-common` to Maven Local.
5. Move `currency-service` to `spring-cloud-platform`.
6. Move `transaction-service`, `permission-service`, and `session-gateway` to `spring-platform`.
7. Remove duplicated consumer version literals and dependency-management plugin usage.
8. Run all validation commands.
9. Update documentation in `service-common` and changed consumer services.
10. After the platform migration is stable, perform Spring Boot, Spring Cloud, and Modulith patch
    upgrades by changing platform versions only, plus Spring Boot Gradle plugin versions in
    consumer catalogs.

## Spring Boot 4.x Preparation

This platform split centralizes dependency BOM selection, but a Spring Boot 4.x migration will
still need additional work:

- Update the Spring Boot Gradle plugin version in each service catalog, or introduce a later
  convention plugin to centralize plugin versions.
- Update `spring-platform` to import the Spring Boot 4 BOM and the compatible Spring Modulith BOM.
- Update `spring-cloud-platform` to import the compatible Spring Cloud release train.
- Update service-common source code for Spring Framework 7, Spring Security 7, Jakarta, and
  Spring Boot autoconfiguration API changes.
- Update service-common Javadoc links.
- Run all consumer builds and local runtime smoke tests.

The platform does not remove source compatibility work, but it makes the dependency selection part
of the migration a single controlled change.

## Autoconfiguration Impact

This work should not change autoconfiguration behavior.

The platform artifacts publish dependency management metadata only. They do not publish:

- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- servlet filters
- WebFlux filters
- security filter chains
- exception handlers
- beans

Autoconfiguration remains owned by `service-core` and `service-web`. The most important guardrail is
to keep servlet, WebFlux, JPA, and Spring Security stack dependencies in `service-web` as
`compileOnly`, so consuming services continue to choose their own runtime stack explicitly.

## Risks And Mitigations

### Platform Modules Accidentally Receive Java-Library Wiring

Risk: The root `subprojects` block applies Java-library configuration to platform projects and
breaks publishing.

Mitigation: Split root configuration by project name before adding platform modules. Publish
platform projects from `components["javaPlatform"]`.

### Maven Platform Does Not Manage Gradle Plugin Versions

Risk: The Spring Boot Gradle plugin version remains duplicated in each service catalog.

Mitigation: Accept this in the first pass. Track a later convention-plugin task if build-script
repetition remains painful.

### Cloud Constraints Appear Too Broad

Risk: Importing Spring Cloud constraints everywhere makes non-Cloud services look Cloud-aware.

Mitigation: Keep Cloud constraints in `spring-cloud-platform` only. Non-Cloud services import
`spring-platform`.

### Constraint Set Becomes A Dumping Ground

Risk: Any service-specific dependency gets added to the platform.

Mitigation: Add a constraint only when it is used by multiple backend services, fills a gap in the
Spring BOMs, or is an intentional ecosystem-wide compatibility override.

### Consumers Rely On Transitive Platform Metadata

Risk: A service depends on `service-web` and assumes version management follows automatically.

Mitigation: Make direct platform import part of the consumer build convention and document it in
README, AGENTS, and consumer docs.

## Completion Criteria

- `service-common` publishes `spring-platform`, `spring-cloud-platform`, `service-core`, and
  `service-web` with one coordinated version.
- `spring-platform` imports Spring Boot and Spring Modulith BOMs and owns shared JVM constraints.
- `spring-cloud-platform` imports `spring-platform` and the Spring Cloud BOM.
- `currency-service` imports `spring-cloud-platform`.
- `transaction-service`, `permission-service`, and `session-gateway` import `spring-platform`.
- Consumer services no longer own Spring Cloud, Spring Modulith, Testcontainers, SpringDoc,
  WireMock, Awaitility, or ShedLock versions when those versions are platform-owned.
- Service dependencies remain explicit.
- `service-web` compile-only stack dependency boundaries remain unchanged.
- All service-common and consumer builds pass.
- Documentation is updated in the same work.
