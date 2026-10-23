# Service Common

> "Archetype: platform. Role: Shared library; provides patterns all Java services consume."
>
> — [AGENTS.md](AGENTS.md#tree-position)

[![Build](https://github.com/budgetanalyzer/service-common/actions/workflows/build.yml/badge.svg)](https://github.com/budgetanalyzer/service-common/actions/workflows/build.yml)

Shared libraries for Budget Analyzer microservices - a personal finance management application.

## Overview

Service Common is a **multi-module Gradle project** that provides common functionality shared across all Budget Analyzer backend microservices. It publishes four artifacts:

### spring-platform
**Purpose**: Base dependency platform for Budget Analyzer Spring services.

**Provides**:
- Spring Boot dependency BOM import
- Spring Modulith dependency BOM import
- Shared version constraints for SpringDoc, WireMock, Awaitility, and ShedLock (Testcontainers is managed by the imported Spring Boot BOM)

**Behavior**: Version management only. It does not add runtime dependencies, application code, or autoconfiguration.

### spring-cloud-platform
**Purpose**: Opt-in Spring Cloud dependency platform for services that use Spring Cloud APIs.

**Provides**:
- spring-platform import
- Spring Cloud dependency BOM import

**Behavior**: Version management only. Non-Cloud services should use spring-platform instead.

### service-core
**Purpose**: Minimal-dependency core utilities for microservices

**Features**:
- Base JPA entity classes (AuditableEntity, SoftDeletableEntity)
- JPA entity listeners and repository utilities
- CSV parsing capabilities (OpenCSV integration)
- Safe logging utilities with sensitive data masking
- Health checks and metrics (Spring Boot Actuator, Prometheus)

**Dependencies**: Spring Data JPA, Spring Boot Actuator, Jackson, SLF4J, OpenCSV

### service-web
**Purpose**: Spring Boot web service components with auto-configuration for servlet and reactive stacks

**Features**:
- Standardized exception handling (@RestControllerAdvice)
- API error response models (ApiErrorResponse)
- HTTP request/response logging filters
- Correlation ID support
- OpenAPI/Swagger base configuration

**Dependencies**: service-core (transitive), Spring Boot Starter WebMVC/WebFlux, SpringDoc OpenAPI

**Note**: Actuator (health checks, metrics) comes from service-core and is available to all services (web and non-web).

**Note**: service-web includes service-core transitively, so most microservices only need to depend on service-web.

## Purpose

Service Common promotes code reuse and consistency across microservices by:
- Reducing code duplication
- Ensuring consistent data models
- Providing shared utilities
- Standardizing error handling and validation

## Technology Stack

- **Java 25**
- **Spring Boot** (Starter WebMVC/WebFlux, Data JPA; version managed by `spring-platform`)
- **SpringDoc OpenAPI** for API documentation
- **OpenCSV** for CSV file processing
- **JUnit Jupiter** for testing

## Usage

For local development, publish these artifacts to Maven Local and consume them from sibling
Budget Analyzer services. CI workflows publish snapshot and release artifacts to GitHub Packages.
Examples below use `<service-common-version>` as a placeholder. Use the checked-in version literal
from `build.gradle.kts` for the build you are consuming.

### Which Module Should I Use?

**Most microservices should use `service-web`** (which transitively includes `service-core`):

```kotlin
dependencies {
    implementation(platform("org.budgetanalyzer:spring-platform:<service-common-version>"))
    implementation("org.budgetanalyzer:service-web:<service-common-version>")
}
```

**Spring Cloud services should use `spring-cloud-platform`** instead of importing both platforms:

```kotlin
dependencies {
    implementation(platform("org.budgetanalyzer:spring-cloud-platform:<service-common-version>"))
    implementation("org.budgetanalyzer:service-web:<service-common-version>")
}
```

**Use `service-core` alone** only if you need minimal dependencies without Spring Web features:

```kotlin
dependencies {
    implementation(platform("org.budgetanalyzer:spring-platform:<service-common-version>"))
    implementation("org.budgetanalyzer:service-core:<service-common-version>")
}
```

### Adding as a Dependency

In your service's `build.gradle.kts`:

```kotlin
dependencies {
    // Import dependency versions explicitly.
    implementation(platform("org.budgetanalyzer:spring-platform:<service-common-version>"))

    // Recommended: Use service-web (includes service-core transitively)
    implementation("org.budgetanalyzer:service-web:<service-common-version>")
}

repositories {
    mavenLocal()
    mavenCentral()
}
```

The platform artifacts only provide dependency version management. They do not provide beans,
filters, exception handlers, or autoconfiguration metadata. Runtime behavior comes from
`service-core` and `service-web`.

### What You Get Automatically

The runtime modules use **Spring Boot autoconfiguration** - all components are automatically registered when the libraries are on your classpath. **No manual configuration or component scanning required!**

#### service-core (Fully Automatic)
When service-core is on your classpath, you automatically get:
- **JPA Entity Scanning** - Base entities (AuditableEntity, SoftDeletableEntity) automatically discovered
- **JPA Auditing** - Automatic timestamps (createdAt, updatedAt) on entities
- **CSV Parser Bean** - OpenCsvParser available for injection
- **Prometheus Metrics** - `/actuator/prometheus` endpoint exposed automatically for scraping
- **`application` Common Tag** - Every Micrometer meter is tagged with `application=${spring.application.name}` so metrics carry service identity independent of the scrape pipeline (override with `management.metrics.tags.application`)

#### service-web (Mostly Automatic)
When service-web is on your classpath, you automatically get:
- **Global Exception Handler** - All exceptions converted to standardized ApiErrorResponse format
  - Maps: 400 (Bad Request), 404 (Not Found), 422 (Business Logic), 500 (Server Error)
- **Claims Header Security** - Automatic stateless authentication from trusted ingress external-auth headers
  - Reads `X-User-Id`, `X-Permissions`, `X-Roles` headers for both servlet and reactive services
  - Public endpoints: health, prometheus, and OpenAPI routes; all other endpoints, including `/internal/**`, require service-owned rules or authenticated claims headers
- **Correlation ID Filter** - Automatically adds correlation IDs to all requests and regenerates malformed inbound values before they reach logs or response headers
- **HTTP Logging Filter** - Optional (enable with `budgetanalyzer.service.http-logging.enabled=true`)
  - Text bodies only: common secret fields in JSON and form payloads are redacted
  - Multipart, binary, and compressed bodies are omitted with placeholders instead of raw content
  - Required list settings and `log-level` must not be explicit null values; startup fails during
    configuration validation if they are bound as null. Empty lists are valid.
- **OpenAPI Base Config** - Additive standard error responses, including 401/403 for
  `@PreAuthorize` operations (extend BaseOpenApiConfig in your service)

**Example configuration** (application.yml):
```yaml
budgetanalyzer:
  service:
    http-logging:
      enabled: true
      include-query-params: false # Optional: defaults to false
      include-request-body: true   # Optional: defaults to false; JSON/form secrets are redacted
      include-response-body: true  # Optional: defaults to false; binary/multipart/compressed bodies are omitted
      max-body-size: 10000         # Optional: values <= 0 disable body capture
```

**No component scanning needed** - Spring Boot autoconfiguration handles everything via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

The shared claims-header security configuration provides the default Spring Security filter chain.
The two stacks handle consumer-defined chains differently. On the servlet stack, the shared chain
matches every request and coexists with consumer chains: to own specific paths, a service registers
a `SecurityFilterChain` with a lower `@Order` and a `securityMatcher`, and requests it does not
match fall through to the shared chain. A servlet consumer chain without a `securityMatcher` makes
one of the chains unreachable, and Spring Security fails at startup. On the reactive stack, the
shared `SecurityWebFilterChain` backs off when a service defines any `SecurityWebFilterChain`
bean. The service's chain then owns all authentication and authorization rules, while
`@PreAuthorize` method security stays enforced.

#### Discovering What's Autoconfigured

```bash
# View autoconfiguration registration files
cat service-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
cat service-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

# View autoconfiguration classes
cat service-core/src/main/java/org/budgetanalyzer/core/config/ServiceCoreAutoConfiguration.java
cat service-web/src/main/java/org/budgetanalyzer/service/config/ServiceWebAutoConfiguration.java

# View global exception handler
cat service-web/src/main/java/org/budgetanalyzer/service/servlet/api/ServletApiExceptionHandler.java

# View security configuration
cat service-web/src/main/java/org/budgetanalyzer/service/security/ClaimsHeaderSecurityConfig.java

# Find all @Configuration classes
grep -r "@Configuration\|@AutoConfiguration" service-*/src/main/java/
```

For complete details, see the [Autoconfiguration section in AGENTS.md](AGENTS.md#autoconfiguration).

### Building and Publishing

```bash
# Build all modules
./gradlew clean build

# Publish all service-common artifacts to Maven Local for local development
./gradlew publishToMavenLocal
```

`publishToMavenLocal` uses the checked-in version literal from
`build.gradle.kts` and is the supported local development path. It does not
require GitHub credentials and does not publish anything remotely. The build
workflow also runs `publishToMavenLocal` as a validation step only.

Remote publishing is handled by this repository's workflows:
`publish-snapshot.yml` publishes `-SNAPSHOT` versions from `main` to GitHub
Packages, and `publish-release.yml` publishes tag-driven releases to GitHub
Packages. Successful remote publishes write the published version and GitHub
Packages links to the workflow log and job summary. See
[docs/versioning-and-compatibility.md](docs/versioning-and-compatibility.md) for
the release and publishing contract.

Renovate update discovery and the trusted `main` resolved dependency graph are
documented in
[docs/dependency-automation.md](docs/dependency-automation.md). Dependency graph
submission inventories BOM-managed transitive packages for Dependabot alerts;
it does not publish service-common artifacts.

`service-common`'s own version and backwards-compatibility contract lives in
[docs/versioning-and-compatibility.md](docs/versioning-and-compatibility.md).

## Development

### Prerequisites

- JDK 25
- Gradle (wrapper included)

### Building

```bash
# Clean and build
./gradlew clean build

# Run tests
./gradlew test

# Run the isolated servlet and reactive consumer-classpath smoke suites (also run by check)
./gradlew :service-web:servletSmokeTest :service-web:reactiveSmokeTest

# Run tests and generate JaCoCo coverage reports
./gradlew test jacocoTestReport

# Check code style
./gradlew spotlessCheck

# Apply code formatting
./gradlew clean spotlessApply
```

JaCoCo report generation is configured for the Java library modules only:
`service-core/build/reports/jacoco/test/html/index.html` and
`service-web/build/reports/jacoco/test/html/index.html`. XML reports are written
next to the HTML reports for CI/dashboard ingestion. `check` enforces the
configured coverage gates.

The coverage reports and gates cover each module's `test` task. `service-web` also
defines `servletSmokeTest` and `reactiveSmokeTest` suites (`service-web/src/servletSmokeTest/`
and `service-web/src/reactiveSmokeTest/`). Each suite depends on the project's own api and
runtime variants through `implementation(project())`, plus a single web stack, Spring Security,
and the matching SpringDoc starter, without JPA or the combined test classpath. They start
minimal fixture applications through normal Boot auto-configuration and verify, over real HTTP:
startup and classpath isolation, shared security and method security wiring, `ApiErrorResponse`
handling, actuator probes and Prometheus, and SpringDoc serving the shared error documentation,
including preservation of explicit controller responses and the generated schema and example
contents.

The `src/<suiteName>/java` and `src/<suiteName>/resources` directories are Gradle JVM test
suite conventions. The isolated suites are permanent guards against optional-dependency and
auto-configuration regressions; selecting an application type on the combined test classpath is
not equivalent, because the other stack and JPA remain on the classpath.

The suites do not exercise the generated POM or Gradle module metadata files, or the versions
written into them by `versionMapping`. Verifying published metadata requires consumer builds
against `publishToMavenLocal`; the smoke suites do not satisfy that gate. Neither suite adds
published dependencies.

### CI Artifacts

The `Build` workflow (`.github/workflows/build.yml`) runs `./gradlew build` as the test gate
and uploads these artifacts to the workflow run, each retained for seven days:

- `test-results`: JUnit XML from every test task (`**/build/test-results/*/*.xml`) and the HTML
  reports with their supporting files (`**/build/reports/tests/**`). Module and task directories
  are preserved, so `service-core` and `service-web` `test`, `servletSmokeTest`, and
  `reactiveSmokeTest` results stay distinguishable. Open `<module>/build/reports/tests/<task>/index.html`
  for the navigable report.
- `publication-metadata`: the generated `pom-default.xml` and Gradle `module.json` for each of
  the four `mavenJava` publications (`**/build/publications/mavenJava/`), produced by
  `generatePomFileForMavenJavaPublication` and `generateMetadataFileForMavenJavaPublication`
  without running any publish task.
- `jars`: the built JARs (uploaded only when the preceding steps succeed).

The report upload, metadata generation, and metadata upload steps run even when an earlier step
fails, and those failures still fail the run. They collect whatever files exist; a compilation or
dependency-resolution failure can leave either artifact empty or partial, and the upload then
warns instead of failing. Archived POM and module files make the generated metadata reviewable,
but they do not exercise consumer dependency resolution and do not replace the consumer-build
release gate described in
[docs/versioning-and-compatibility.md](docs/versioning-and-compatibility.md).

## Code Quality

This project enforces code quality through:
- **Google Java Format** for consistent code style
- **Checkstyle** for code standards
- **Spotless** for automated formatting

## Conventions

### Prefixed String IDs

All cross-service identifiers use the format `{prefix}_{full-uuid-hex}` (e.g., `usr_507f1f77bcf86cd799439011abcdef12`, `req_a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6`).

- **Why strings over longs**: These IDs flow across service boundaries and into JWTs. Auto-increment longs couple identity to a single database; string UUIDs are portable by default.
- **Why prefixes**: `usr_`, `txn_`, `req_` make IDs self-describing in logs, JWTs, and database queries.
- **Why full UUIDs**: Always use 32 hex chars (128 bits). Never truncate — entropy reduction with no storage benefit since the type is `String` either way.

See the [Vendor Independence section in AGENTS.md](AGENTS.md#vendor-independence) for the full rationale.

## Project Structure

```
service-common/
├── spring-platform/                 # Base Spring dependency platform
│   └── build.gradle.kts
├── spring-cloud-platform/           # Spring Cloud dependency platform overlay
│   └── build.gradle.kts
├── service-core/                    # Core utilities module
│   ├── src/main/java/
│   │   └── org/budgetanalyzer/core/
│   │       ├── domain/              # Base JPA entities
│   │       ├── repository/          # Repository utilities
│   │       ├── csv/                 # CSV parsing
│   │       └── logging/             # Safe logging
│   ├── src/test/java/
│   └── build.gradle.kts
├── service-web/                     # Spring Boot web module
│   ├── src/main/java/
│   │   └── org/budgetanalyzer/service/
│   │       ├── exception/           # Exception hierarchy
│   │       ├── api/                 # Error response models
│   │       ├── http/                # HTTP filters/logging
│   │       └── config/              # OpenAPI config
│   ├── src/test/java/
│   ├── src/servletSmokeTest/        # Isolated servlet consumer-classpath smoke suite
│   ├── src/reactiveSmokeTest/       # Isolated reactive consumer-classpath smoke suite
│   └── build.gradle.kts
├── build.gradle.kts                 # Root build configuration
├── settings.gradle.kts              # Multi-module setup
└── config/                          # Checkstyle configuration
```

## Related Repositories

- **Orchestration**: https://github.com/budgetanalyzer/orchestration
- **Session Gateway**: https://github.com/budgetanalyzer/session-gateway
- **Token Validation Service**: https://github.com/budgetanalyzer/token-validation-service
- **Permission Service**: https://github.com/budgetanalyzer/permission-service
- **Transaction Service**: https://github.com/budgetanalyzer/transaction-service
- **Currency Service**: https://github.com/budgetanalyzer/currency-service
- **Web Frontend**: https://github.com/budgetanalyzer/budget-analyzer-web

## License

MIT

## Contributing

This project is currently in early development. Contributions, issues, and feature requests are welcome as we build toward a stable release.
