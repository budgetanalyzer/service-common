# Service Common

[![Build](https://github.com/budgetanalyzer/service-common/actions/workflows/build.yml/badge.svg)](https://github.com/budgetanalyzer/service-common/actions/workflows/build.yml)

Shared libraries for Budget Analyzer microservices - a personal finance management application.

## Overview

Service Common is a **multi-module Gradle project** that provides common functionality shared across all Budget Analyzer backend microservices. It consists of two modules:

### service-core
**Purpose**: Minimal-dependency core utilities for microservices

**Features**:
- Base JPA entity classes (AuditableEntity, SoftDeletableEntity)
- JPA entity listeners and repository utilities
- CSV parsing capabilities (OpenCSV integration)
- Safe logging utilities with sensitive data masking
- Health checks and metrics (Spring Boot Actuator)

**Dependencies**: Spring Data JPA, Spring Boot Actuator, Jackson, SLF4J, OpenCSV

### service-web
**Purpose**: Spring Boot web service components with auto-configuration

**Features**:
- Standardized exception handling (@RestControllerAdvice)
- API error response models (ApiErrorResponse)
- HTTP request/response logging filters
- Correlation ID support
- OpenAPI/Swagger base configuration

**Dependencies**: service-core (transitive), Spring Boot Starter Web, SpringDoc OpenAPI

**Note**: Actuator (health checks, metrics) comes from service-core and is available to all services (web and non-web).

**Note**: service-web includes service-core transitively, so most microservices only need to depend on service-web.

## Purpose

Service Common promotes code reuse and consistency across microservices by:
- Reducing code duplication
- Ensuring consistent data models
- Providing shared utilities
- Standardizing error handling and validation

## Technology Stack

- **Java 24**
- **Spring Boot 3.x** (Starter Web, Data JPA)
- **SpringDoc OpenAPI** for API documentation
- **OpenCSV** for CSV file processing
- **JUnit 5** for testing

## Usage

These libraries are published to Maven Local and consumed by other Budget Analyzer services.

### Which Module Should I Use?

**Most microservices should use `service-web`** (which transitively includes `service-core`):

```kotlin
dependencies {
    implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
}
```

**Use `service-core` alone** only if you need minimal dependencies without Spring Web features:

```kotlin
dependencies {
    implementation("org.budgetanalyzer:service-core:0.0.1-SNAPSHOT")
}
```

### Adding as a Dependency

In your service's `build.gradle.kts`:

```kotlin
dependencies {
    // Recommended: Use service-web (includes service-core transitively)
    implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
}

repositories {
    mavenLocal()
    mavenCentral()
}
```

### What You Get Automatically

Both modules use **Spring Boot autoconfiguration** - all components are automatically registered when the libraries are on your classpath. **No manual configuration or component scanning required!**

#### service-core (Fully Automatic)
When service-core is on your classpath, you automatically get:
- **JPA Entity Scanning** - Base entities (AuditableEntity, SoftDeletableEntity) automatically discovered
- **JPA Auditing** - Automatic timestamps (createdAt, updatedAt) on entities
- **CSV Parser Bean** - OpenCsvParser available for injection

#### service-web (Mostly Automatic)
When service-web is on your classpath, you automatically get:
- **Global Exception Handler** - All exceptions converted to standardized ApiErrorResponse format
  - Maps: 400 (Bad Request), 404 (Not Found), 422 (Business Logic), 500 (Server Error)
- **OAuth2 JWT Security** - Automatic JWT validation with Auth0
  - Requires: `spring.security.oauth2.resourceserver.jwt.issuer-uri` configuration
- **Correlation ID Filter** - Automatically adds correlation IDs to all requests
- **HTTP Logging Filter** - Optional (enable with `budgetanalyzer.service.http-logging.enabled=true`)
- **OpenAPI Base Config** - Standard error response schemas (extend BaseOpenApiConfig in your service)

**Example configuration** (application.yml):
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${AUTH0_ISSUER_URI}

budgetanalyzer:
  service:
    http-logging:
      enabled: true  # Optional: enable HTTP request/response logging
```

**No component scanning needed** - Spring Boot autoconfiguration handles everything via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

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
cat service-web/src/main/java/org/budgetanalyzer/service/security/OAuth2ResourceServerSecurityConfig.java

# Find all @Configuration classes
grep -r "@Configuration\|@AutoConfiguration" service-*/src/main/java/
```

For complete details, see the [Autoconfiguration section in CLAUDE.md](CLAUDE.md#autoconfiguration).

### Building and Publishing

```bash
# Build all modules
./gradlew clean build

# Publish both modules to Maven Local
./gradlew publishToMavenLocal
```

This publishes both artifacts:
- `org.budgetanalyzer:service-core:0.0.1-SNAPSHOT`
- `org.budgetanalyzer:service-web:0.0.1-SNAPSHOT`

## Development

### Prerequisites

- JDK 24
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

## Project Structure

```
service-common/
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
- **Transaction Service**: https://github.com/budgetanalyzer/transaction-service
- **Currency Service**: https://github.com/budgetanalyzer/currency-service
- **Web Frontend**: https://github.com/budgetanalyzer/budget-analyzer-web

## License

MIT

## Contributing

This project is currently in early development. Contributions, issues, and feature requests are welcome as we build toward a stable release.
