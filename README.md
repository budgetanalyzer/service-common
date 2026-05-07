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
- Shared version constraints for SpringDoc, Testcontainers, WireMock, Awaitility, and ShedLock

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

**Dependencies**: service-core (transitive), Spring Boot Starter Web/WebFlux, SpringDoc OpenAPI

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
- **Spring Boot 3.x** (Starter Web, Data JPA)
- **SpringDoc OpenAPI** for API documentation
- **OpenCSV** for CSV file processing
- **JUnit 5** for testing

## Usage

For local development, publish these artifacts to Maven Local and consume them from sibling
Budget Analyzer services. CI workflows publish snapshot and release artifacts to GitHub Packages.
Examples below use `<service-common-version>` as a placeholder. Use the checked-in version literal
from `build.gradle.kts` for the build you are consuming (for example, `0.0.1-SNAPSHOT` during local
snapshot development).

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
- **OpenAPI Base Config** - Standard error response schemas (extend BaseOpenApiConfig in your service)

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
If a consuming service defines its own `SecurityFilterChain` or `SecurityWebFilterChain`, the
shared default chain backs off so the service's custom rules win deterministically.

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
Packages. See [docs/versioning-and-compatibility.md](docs/versioning-and-compatibility.md)
for the release and publishing contract.

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

# Check code style
./gradlew spotlessCheck

# Apply code formatting
./gradlew clean spotlessApply
```

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
