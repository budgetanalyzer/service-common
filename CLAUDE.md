# Service-Common - Shared Spring Boot Libraries

## Purpose

Multi-module Gradle project providing shared libraries for all Budget Analyzer Spring Boot microservices. Consists of two modules:

**service-core**: Minimal-dependency core utilities (base entities, CSV parsing, safe logging)
**service-web**: Spring Boot web components (exception handling, API error responses, HTTP logging, OpenAPI config)

**Impacts**: transaction-service, currency-service, and all future Spring Boot services.
Changes here affect all services that depend on these libraries.

## Module Architecture

### service-core
**Location**: `service-core/src/main/java/org/budgetanalyzer/core/`

**Purpose**: Domain-agnostic utilities with minimal dependencies

**Contains**:
- `domain/` - Base JPA entities (AuditableEntity, SoftDeletableEntity)
- `repository/` - Repository utilities (SoftDeleteOperations)
- `csv/` - CSV parsing (CsvParser, CsvData, OpenCsvParser)
- `logging/` - Safe logging with sensitive data masking

**Dependencies**: Spring Data JPA, Spring Boot Actuator, Jackson, SLF4J, OpenCSV

### service-web
**Location**: `service-web/src/main/java/org/budgetanalyzer/service/`

**Purpose**: Spring Boot web components supporting both servlet and reactive stacks

**Contains**:
- `api/` - Shared error response models (ApiErrorResponse, ApiErrorType, FieldError, ApiExceptionHandler)
- `exception/` - Exception hierarchy (shared by both stacks: ResourceNotFoundException, BusinessException, etc.)
- `config/` - Shared configuration (HttpLoggingProperties, ServiceWebAutoConfiguration, BaseOpenApiConfig)
- `servlet/` - Servlet-specific implementations (Spring MVC)
  - `http/` - HTTP filters (CorrelationIdFilter, HttpLoggingFilter, ContentLoggingUtil, HttpLoggingConfig)
  - `api/` - Exception handler (ServletApiExceptionHandler)
- `reactive/` - Reactive-specific implementations (Spring WebFlux)
  - `http/` - Reactive filters (ReactiveCorrelationIdFilter, ReactiveHttpLoggingFilter, body caching decorators, ReactiveHttpLoggingConfig)
  - `api/` - Exception handler (ReactiveApiExceptionHandler)

**Dependencies**: service-core (transitive), Spring Boot Starter Web (compileOnly), Spring Boot Starter WebFlux (compileOnly), SpringDoc OpenAPI (compileOnly)

**Note**: service-web now supports BOTH servlet and reactive stacks through conditional autoconfiguration. Consuming services must explicitly declare their web stack dependency (see Breaking Changes section).

**Note**: Actuator comes from service-core, available to all services (web and non-web)

**Note**: service-web transitively includes service-core, so consuming services typically only need service-web.

## When to Use These Libraries

- ✅ Cross-service utilities (logging, error handling, common DTOs)
- ✅ Shared Spring Boot configurations
- ✅ Common dependencies and version management
- ❌ Service-specific business logic (belongs in service repos)

### Which Module to Depend On?

**Most microservices → Use service-web** (includes service-core transitively)
**Need only base entities/CSV/logging → Use service-core** (minimal dependencies)

## Breaking Changes (Version 0.0.1-SNAPSHOT)

### JPA Dependency Management

**BREAKING**: service-core now declares `spring-boot-starter-data-jpa` as `compileOnly` instead of `implementation`. Services using JPA entities (`AuditableEntity`, `SoftDeletableEntity`) or repository utilities (`SoftDeleteOperations`) must explicitly declare the JPA dependency.

**Required for services using JPA entities:**
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")  // NOW REQUIRED
    implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
}
```

**Why?** This prevents non-database services (like reactive gateways) from inheriting unnecessary JPA and database dependencies through service-web → service-core transitive dependency chain.

**Who needs to add this:**
- Services using `AuditableEntity` or `SoftDeletableEntity` base classes
- Services using `SoftDeleteOperations` repository utilities
- Any service with JPA repositories and database persistence

**Who does NOT need this:**
- Pure web services without databases (e.g., session-gateway)
- Services only using service-core's CSV parsing or logging utilities

### Web Stack Dependency Management

**BREAKING**: As of version 0.0.1-SNAPSHOT, consuming services must explicitly declare their web stack dependency.

**Servlet services:**
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")  // NOW REQUIRED
    implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
}
```

**Reactive services:**
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")  // NOW REQUIRED
    implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
}
```

**Why?** This prevents classpath conflicts where reactive services would inherit servlet dependencies (and vice versa).

### Package Reorganization

Components have been reorganized to separate servlet and reactive implementations:

| Old Package | New Package | Type |
|-------------|-------------|------|
| `org.budgetanalyzer.service.http.*` | `org.budgetanalyzer.service.servlet.http.*` | Servlet |
| `org.budgetanalyzer.service.api.ServletApiExceptionHandler` | `org.budgetanalyzer.service.servlet.api.ServletApiExceptionHandler` | Servlet |
| N/A | `org.budgetanalyzer.service.reactive.http.*` | Reactive (NEW) |
| N/A | `org.budgetanalyzer.service.reactive.api.*` | Reactive (NEW) |
| `org.budgetanalyzer.service.http.HttpLoggingProperties` | `org.budgetanalyzer.service.config.HttpLoggingProperties` | Shared |

**Impact**: Internal to service-web only. Consuming services don't import these classes directly since they're registered via Spring Boot autoconfiguration.

## Spring Boot Conventions

**THE canonical source of truth** for Spring Boot patterns across all Budget Analyzer microservices.

**Pattern**: Clean layered architecture (Controller → Service → Repository) with standardized naming, pure JPA persistence, and base entity classes for common functionality.

**When to consult details**:
- Setting up new service → Read [Architecture Layers](docs/spring-boot-conventions.md#architecture-layers) and [Package Structure](docs/spring-boot-conventions.md#package-structure)
- Creating entities → See [Base Entity Classes](docs/spring-boot-conventions.md#base-entity-classes) (AuditableEntity, SoftDeletableEntity)
- Writing controllers → Review [HTTP Response Patterns](docs/spring-boot-conventions.md#http-response-patterns) (201 Created with Location header)
- Dependency injection → Check [Dependency Injection](docs/spring-boot-conventions.md#dependency-injection) section

**Quick reference**:
- Controllers: `*Controller` + `@RestController` + thin (HTTP concerns only)
- Services: `*Service` concrete classes + `@Service` + `@Transactional` at this layer
- Repositories: `*Repository` extends `JpaRepository<Entity, ID>`
- Providers: `*Provider` interface + implementation (for external service boundaries only)
- Pure JPA only: **Forbidden** `org.hibernate.*` → **Use** `jakarta.persistence.*`
- Base entities: Extend `AuditableEntity` (timestamps) or `SoftDeletableEntity` (soft delete)

**For comprehensive patterns, read [docs/spring-boot-conventions.md](docs/spring-boot-conventions.md) when working on architecture tasks.**

**Discovery**:
```bash
# View service-core package structure
find service-core/src/main/java -type d | grep -E "org/budgetanalyzer"

# View service-web package structure
find service-web/src/main/java -type d | grep -E "org/budgetanalyzer"

# Find all base entities (in service-core)
grep -r "@MappedSuperclass" service-core/src/
```

## Exception Handling

**Module**: service-web

**Pattern**: Centralized `@RestControllerAdvice` handler that converts all exceptions to standardized `ApiErrorResponse` format with HTTP status codes, error types, and field-level validation messages.

**When to consult details**:
- Throwing exceptions in code → See [Exception Hierarchy](docs/error-handling.md#exception-hierarchy) for which exception to use
- Creating custom exceptions → Read [Custom Exception Examples](docs/error-handling.md#custom-exceptions)
- Handling validation errors → Review [Validation Error Handling](docs/error-handling.md#validation-errors)
- Understanding error responses → Check [ApiErrorResponse Format](docs/error-handling.md#apierrorresponse-format)

**Quick reference**:
- `ResourceNotFoundException` → 404 (entity not found)
- `InvalidRequestException` → 400 (bad input data)
- `BusinessException` → 422 (business rule violation)
- `ServiceException` → 500 (internal service error)
- All exceptions auto-converted to `ApiErrorResponse` by `ServletApiExceptionHandler`

**For complete error handling patterns, read [docs/error-handling.md](docs/error-handling.md) when implementing error flows.**

**Discovery**:
```bash
# Find all custom exceptions (in service-web)
grep -r "extends.*Exception" service-web/src/ | grep -v "Test"

# View exception handler
cat service-web/src/main/java/org/budgetanalyzer/service/api/ServletApiExceptionHandler.java
```

## Testing Patterns

**Pattern**: Comprehensive test coverage using unit tests for logic, integration tests with TestContainers for persistence, and test correct behavior (never test around bugs).

**When to consult details**:
- Writing unit tests → See [Unit Testing Patterns](docs/testing-patterns.md#unit-tests) (mocking, naming, structure)
- Writing integration tests → Read [Integration Testing](docs/testing-patterns.md#integration-tests) (TestContainers, @SpringBootTest)
- Setting up TestContainers → Check [TestContainers Setup](docs/testing-patterns.md#testcontainers) (PostgreSQL, Redis, RabbitMQ)
- Understanding test philosophy → Review [Testing Philosophy](docs/testing-patterns.md#philosophy) (test correct behavior)

**Quick reference**:
- Unit tests: `*Test.java` (no Spring context, fast, isolated)
- Integration tests: `*IntegrationTest.java` (with `@SpringBootTest` + TestContainers)
- Minimum coverage: 80% line coverage via JaCoCo
- Test correct behavior: Fix bugs, don't write tests around defective implementations
- TestContainers: Auto-starts PostgreSQL/Redis/RabbitMQ in Docker for integration tests

**For comprehensive testing strategies, read [docs/testing-patterns.md](docs/testing-patterns.md) when writing tests.**

**Discovery**:
```bash
# View test structure in service-core
find service-core/src/test/java -name "*Test.java"

# View test structure in service-web
find service-web/src/test/java -name "*Test.java"

# Run tests with coverage (all modules)
./gradlew test jacocoTestReport
```

## Key Features

**Pattern**: Shared utilities and base classes for cross-cutting concerns.

**Quick reference by module**:

**service-core**:
- Base Entities: `AuditableEntity` (timestamps + user tracking), `SoftDeletableEntity` (soft delete)
- CSV Parsing: `CsvParser`, `CsvData`, `OpenCsvParser`
- Safe Logging: `SafeLogger`, `@Sensitive` annotation, `SensitiveDataModule`
- Repository: `SoftDeleteOperations`
- User Tracking: `SecurityContextAuditorAware` (auto-populates createdBy/updatedBy from Security context)

**service-web**:
- Exceptions: `ResourceNotFoundException`, `BusinessException`, `InvalidRequestException`, `ServiceException`
- API Models: `ApiErrorResponse`, `ApiErrorType`, `FieldError`
- HTTP Filters: `CorrelationIdFilter`, `HttpLoggingFilter`
- Configuration: `BaseOpenApiConfig`, `HttpLoggingConfig`

**Discovery**:
```bash
# View base entities (service-core)
grep -r "@MappedSuperclass" service-core/src/

# View exception hierarchy (service-web)
grep -r "extends.*Exception" service-web/src/ | grep -v "Test"

# View service-core utilities
find service-core/src/main/java -name "*.java"

# View service-web components
find service-web/src/main/java -name "*.java"
```

## Publishing and Consumption

### Publish to Maven Local
```bash
# Build and publish both modules
./gradlew clean spotlessApply
./gradlew clean build
./gradlew publishToMavenLocal
```

**Maven Coordinates** (both modules published):
```groovy
// service-core
groupId: org.budgetanalyzer
artifactId: service-core
version: 0.0.1-SNAPSHOT

// service-web
groupId: org.budgetanalyzer
artifactId: service-web
version: 0.0.1-SNAPSHOT
```

### Consume in Microservices

**Recommended**: Use service-web (includes service-core transitively)
```kotlin
// build.gradle.kts
dependencies {
    implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
}
```

**Alternative**: Use service-core only (minimal dependencies)
```kotlin
// build.gradle.kts
dependencies {
    implementation("org.budgetanalyzer:service-core:0.0.1-SNAPSHOT")
}
```

**Spring Boot Autoconfiguration**: Both modules automatically register their components via Spring Boot's autoconfiguration mechanism. **No manual component scanning or @ComponentScan is required** - exception handling, security, filters, and all other components are automatically available.

**Optional**: If your service uses non-standard package structure (not `@SpringBootApplication` in root package), you may need explicit component scanning:
```java
@SpringBootApplication(scanBasePackages = {
    "org.budgetanalyzer.yourservice"  // Your service packages
})
```
Note: This is for **your service's components only** - service-common components are already autoconfigured.

## Autoconfiguration

**Pattern**: Both modules use Spring Boot autoconfiguration mechanism - consuming services get most functionality automatically without manual configuration or component scanning.

**Discovery**:
```bash
# View autoconfiguration files
cat service-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
cat service-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

# See what's registered
grep -r "@Configuration\|@AutoConfiguration" service-*/src/main/java/
```

### service-core: Automatic Configuration

**Registered via**: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

#### What You Get Automatically

**ServiceCoreAutoConfiguration** - Activated when service-core is on classpath:
- **JPA Entity Scanning** (`@EntityScan`) - Automatically scans `org.budgetanalyzer.core.domain` for base entities (AuditableEntity, SoftDeletableEntity)
  - Conditional: Only when `DataSource` exists
- **JPA Auditing** (`@EnableJpaAuditing`) - Enables automatic timestamps and user tracking on entities extending AuditableEntity
  - Conditional: Only when `DataSource` exists
  - Provides `createdAt`, `updatedAt` (timestamps) and `createdBy`, `updatedBy` (user tracking)
- **SecurityContextAuditorAware Bean** - Provides current user ID for JPA auditing
  - Extracts user ID from `Authentication.getName()` in Spring Security context
  - Typically contains Auth0 user ID (e.g., `auth0|507f1f77bcf86cd799439011`)
  - Returns empty for anonymous users or system operations (fields remain null)
- **OpenCsvParser Bean** - CSV parser component automatically available for injection

#### What Requires Manual Use

These are utilities (not Spring beans) - instantiate manually when needed:
- `SafeLogger` - Static utility for safe logging with sensitive data masking
- `SensitiveDataModule` - Jackson module for `@Sensitive` annotation
- Base entity classes (`AuditableEntity`, `SoftDeletableEntity`) - Extend in your entities
- `SoftDeleteOperations` - Repository utility interface

**Discovery**:
```bash
# View ServiceCoreAutoConfiguration
cat service-core/src/main/java/org/budgetanalyzer/core/config/ServiceCoreAutoConfiguration.java

# Find all @Component beans in service-core
grep -r "@Component" service-core/src/main/java/
```

### service-web: Automatic Configuration

**Registered via**: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

#### What You Get Automatically

**ServiceWebAutoConfiguration** - Conditionally registers servlet OR reactive components based on classpath:

**For Servlet Applications** (Spring MVC):
- Activates when `@ConditionalOnWebApplication(type = SERVLET)`
- **ServletApiExceptionHandler** - Global exception handling with `@RestControllerAdvice`
  - Converts all exceptions to standardized `ApiErrorResponse` format
  - Handles: `InvalidRequestException` (400), `ResourceNotFoundException` (404), `BusinessException` (422), `ServiceException` (500), validation errors, generic exceptions
  - Priority: `@Order(LOWEST_PRECEDENCE)` - services can override
- **CorrelationIdFilter** - Always enabled - Adds correlation ID (MDC + response header)
- **HttpLoggingFilter** - Opt-in via configuration
- **HttpLoggingConfig** - Configuration for servlet filters

**For Reactive Applications** (Spring WebFlux):
- Activates when `@ConditionalOnWebApplication(type = REACTIVE)`
- **ReactiveApiExceptionHandler** - Global exception handling with `@ControllerAdvice`
  - Converts all exceptions to standardized `ApiErrorResponse` format
  - Handles reactive-specific validation (`WebExchangeBindException`) plus all standard exceptions
  - Returns `Mono<ResponseEntity<ApiErrorResponse>>`
- **ReactiveCorrelationIdFilter** - Always enabled - Adds correlation ID (Reactor Context + response header)
- **ReactiveHttpLoggingFilter** - Opt-in via configuration
- **ReactiveHttpLoggingConfig** - Configuration for reactive filters

**Shared Configuration**:
- **HttpLoggingProperties** - Shared between servlet and reactive
- **OAuth2ResourceServerSecurityConfig** - JWT security (servlet and reactive compatible)
  - Public endpoints: `/actuator/health/**`
  - Protected: All other endpoints (requires valid JWT)
  - `JwtDecoder` bean (conditional - only if not already defined)

**Enable HTTP logging** (optional, works for both stacks):
```yaml
budgetanalyzer:
  service:
    http-logging:
      enabled: true
      include-query-string: true
      include-client-info: true
      include-headers: true
      include-payload: true
      max-payload-length: 10000
```

**OAuth2 configuration** (required for secured endpoints):
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${AUTH0_ISSUER_URI}  # Auth0 tenant URL

# Environment variable (defaults to https://api.budgetanalyzer.org)
AUTH0_AUDIENCE: ${AUTH0_AUDIENCE}
```

**OpenAPI Base Configuration** (`BaseOpenApiConfig`):
- Abstract base class for service-specific OpenAPI configuration
- Automatically provides: Standard error response schemas (400/404/500/503)
- **Requires manual setup**: Service must extend `BaseOpenApiConfig`
  ```java
  @Configuration
  @OpenApiDefinition(
      info = @Info(title = "Your Service API", version = "1.0.0")
  )
  public class OpenApiConfig extends BaseOpenApiConfig {}
  ```

#### What Requires Manual Use

These are utilities (not Spring beans) or exception classes for throwing:
- **SecurityContextUtil** - Static utility for extracting JWT user info
- **ContentLoggingUtil** - Utility for HTTP content logging
- **Exception classes** - Throw these in your code: `ResourceNotFoundException`, `InvalidRequestException`, `BusinessException`, `ServiceException`, `ClientException`, `ServiceUnavailableException`
- **API response models** - DTOs for error responses: `ApiErrorResponse`, `ApiErrorType`, `FieldError`
- **Test utilities** - `TestSecurityConfig`, `JwtTestBuilder` (for tests)

**Discovery**:
```bash
# View all autoconfiguration classes
cat service-web/src/main/java/org/budgetanalyzer/service/config/ServiceWebAutoConfiguration.java
cat service-web/src/main/java/org/budgetanalyzer/service/security/OAuth2ResourceServerSecurityConfig.java

# View global exception handler
cat service-web/src/main/java/org/budgetanalyzer/service/api/ServletApiExceptionHandler.java

# View HTTP filters
cat service-web/src/main/java/org/budgetanalyzer/service/http/HttpLoggingConfig.java

# Find all @Configuration classes
grep -r "@Configuration" service-web/src/main/java/
```

### Summary: Action Required by Consuming Services

**service-core** (fully automatic):
- ✅ Nothing - works automatically when on classpath
- ✅ Base entities, auditing, CSV parser all autoconfigured

**service-web** (mostly automatic):
- ✅ Exception handling - automatic
- ✅ Security - automatic (requires OAuth2 properties in config)
- ✅ Correlation ID filter - automatic
- ⚙️ HTTP logging - opt-in via `budgetanalyzer.service.http-logging.enabled=true`
- ⚙️ OpenAPI - extend `BaseOpenApiConfig` with `@Configuration` + `@OpenApiDefinition`

**IMPORTANT**: Component scanning of `org.budgetanalyzer.service` is **NOT required** for autoconfiguration. The Spring Boot autoconfiguration mechanism handles everything automatically via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

## Architectural Principles

### Production-Quality Code
All code must be production-ready. No shortcuts, prototypes, or workarounds.

**When to read more**: [docs/common-patterns.md](docs/common-patterns.md) - SOLID principles, design patterns, Spring Boot patterns, database patterns, security/performance best practices.

### Pure JPA Only
**CRITICAL**: Use pure JPA (Jakarta Persistence API) exclusively. NO Hibernate-specific features.
- Forbidden: `org.hibernate.*`
- Allowed: `jakarta.persistence.*`

### Standardized Error Responses
All API errors follow `ApiErrorResponse` format with error types, field-level validation, and error codes.

### Soft Delete Pattern
Entities extending `SoftDeletableEntity` are never actually deleted from the database - only marked as deleted.

### Backwards Compatibility: CI/CD-Driven Lockstep Upgrades
**CRITICAL**: ALL changes to service-core and service-web MUST be backwards compatible. We maintain a common platform across all microservices and upgrade all services in lockstep when we upgrade these libraries.

**How it works**: Pushes to service-common trigger automated CI/CD releases of all microservices. All services are always on the latest version - no version tracking mental overhead, no "which services are on which version?" confusion.

**Why lockstep over independent versioning**:
- **Eliminates version drift**: Integration issues surface immediately during the coordinated upgrade, not weeks later
- **Simpler operations**: One version across all services means consistent behavior and simpler debugging
- **Reduced mental overhead**: No compatibility matrices to maintain, no version mismatch investigations

**Note**: Both modules (service-core and service-web) are versioned together and released as a coordinated pair.

**When to consult details**:
- Determining if a change is breaking → See [What's Breaking vs. Safe](docs/versioning-and-compatibility.md#examples-whats-breaking-vs-safe)
- Deprecating APIs → Read [Deprecation Process](docs/versioning-and-compatibility.md#3-deprecate-before-removal)
- Database schema changes → Review [Database Migrations](docs/versioning-and-compatibility.md#database-migrations)
- Version bumps → Check [Semantic Versioning](docs/versioning-and-compatibility.md#semantic-versioning) and [Version Bump Checklist](docs/versioning-and-compatibility.md#version-bump-checklist)

**Quick reference**:
- Add new, don't modify existing (extend rather than change)
- Test against all consuming services before release
- Major version bumps require coordinated migration across all services

**For comprehensive compatibility guidelines, read [docs/versioning-and-compatibility.md](docs/versioning-and-compatibility.md) when making any changes to service-core or service-web.**

### Code Quality Standards
- **Spotless**: Google Java Format
- **Checkstyle**: Custom rules (including Hibernate import ban)
- **Variable declarations**: Use `var` whenever possible
- **Variable naming**: Class-level fields MUST use full class name (e.g., `currencySeriesRepository` not `repository`)
- **Imports**: No wildcard imports, always explicit
- **Javadoc**: First sentence MUST end with period (`.`)

**When to read more**: [docs/code-quality-standards.md](docs/code-quality-standards.md) - Complete Spotless/Checkstyle configuration, detailed formatting rules, IDE setup, troubleshooting.

**Build commands**:
```bash
# Always run these two commands in sequence
./gradlew clean spotlessApply
./gradlew clean build
```

## Advanced Patterns

**When to use advanced patterns**: External integrations, messaging, caching, distributed systems, scheduled tasks.

**Pattern**: Provider abstraction (external APIs), Transactional Outbox (guaranteed messaging), Redis caching (performance), ShedLock (distributed locking), Flyway (database migrations).

**When to consult details**:
- Integrating external APIs → Read [Provider Abstraction Pattern](docs/advanced-patterns.md#provider-abstraction-pattern)
- Event-driven messaging → See [Transactional Outbox](docs/advanced-patterns.md#event-driven-messaging-with-transactional-outbox)
- Implementing caching → Review [Redis Distributed Caching](docs/advanced-patterns.md#redis-distributed-caching)
- Scheduled tasks in multi-pod deployment → Check [ShedLock](docs/advanced-patterns.md#distributed-locking-with-shedlock)
- Database schema changes → Read [Flyway Migrations](docs/advanced-patterns.md#database-migrations-with-flyway)

**Quick reference**:
- Provider Pattern: Service → Interface → Implementation → External Client (decouples from external dependencies)
- Transactional Outbox: Events persisted in DB with business data, 100% guaranteed delivery to RabbitMQ
- Redis Caching: `@Cacheable` on queries, `@CacheEvict` on updates (50-200x faster responses)
- ShedLock: `@SchedulerLock` on `@Scheduled` methods (runs once across all pods)
- Flyway: `V{version}__{description}.sql` migrations (never modify committed migrations)

**For advanced implementation details, read [docs/advanced-patterns.md](docs/advanced-patterns.md) when implementing these features.**

## Best Practices

### Adding to service-common

**When to add**:
- Used by 2+ services
- Cross-cutting concern (logging, error handling, security)
- Common DTOs or utilities

**When NOT to add**:
- Service-specific business logic
- One-off utilities
- Domain models (unless truly shared)

**Process**:
1. Verify need (is this really shared?)
2. Add to service-common with tests
3. Version bump (semantic versioning)
4. Publish: `./gradlew publishToMavenLocal`
5. Update consuming services
6. Document changes

### Library Design Principles

**Backward compatibility** - ALL changes must be backwards compatible to support lockstep upgrades across all microservices. Breaking changes force version fragmentation and deployment coordination problems. We upgrade all services together when we upgrade service-common to catch integration issues immediately and maintain a common platform. Add new methods/classes rather than modifying existing ones. Major version bumps require coordinated migration across all services.

**Minimal dependencies** - Only include essential dependencies

**Clear package separation** - Core (domain-agnostic) vs. Service (Spring-specific)

**Comprehensive Javadoc** - All public APIs must be documented

## Notes for Claude Code

**CRITICAL - Prerequisites First**: Before implementing any plan or feature:
1. Check for prerequisites in documentation (e.g., "Prerequisites: service-common Enhancement")
2. If prerequisites are NOT satisfied, STOP immediately and inform the user
3. Do NOT attempt to hack around missing prerequisites - this leads to broken implementations that must be deleted
4. Complete prerequisites first, then return to the original task

### Critical Rules
**All code must be production-quality** - No shortcuts, prototypes, or workarounds
**Use pure JPA only** - No Hibernate-specific imports (`org.hibernate.*`)
**Maintain backwards compatibility** - All service-common changes must work with existing consuming services (lockstep upgrade strategy)
**Always run build commands**:
   ```bash
   ./gradlew clean spotlessApply
   ./gradlew clean build
   ```
**Fix Checkstyle warnings** - Treat warnings as errors requiring immediate resolution

### Common Checkstyle Issues
- Javadoc missing periods at end of first sentence
- Wildcard imports (use explicit imports)
- Hibernate imports (forbidden)
- Missing Javadoc on public methods/classes

### Testing Requirements
- Write tests for all new features
- Minimum 80% coverage
- Test edge cases explicitly
- Test correct behavior, not defective implementations (fix bugs, don't test around them)

## Documentation References

**Core patterns** (load when working on related tasks):
- [docs/spring-boot-conventions.md](docs/spring-boot-conventions.md) - Architecture, layers, naming, base entities, HTTP patterns
- [docs/error-handling.md](docs/error-handling.md) - Exception hierarchy, ApiErrorResponse format, error handling
- [docs/testing-patterns.md](docs/testing-patterns.md) - Unit/integration testing, TestContainers, coverage goals

**Advanced patterns** (load when implementing specific features):
- [docs/common-patterns.md](docs/common-patterns.md) - SOLID principles, design patterns, database/security/performance best practices
- [docs/advanced-patterns.md](docs/advanced-patterns.md) - Provider abstraction, messaging, caching, distributed locking, migrations
- [docs/code-quality-standards.md](docs/code-quality-standards.md) - Spotless/Checkstyle details, formatting rules, IDE configuration

**System architecture** (load when understanding cross-service concerns):
- System-wide architecture: [orchestration/CLAUDE.md](../orchestration/CLAUDE.md)
- Individual service CLAUDE.md files - Service-specific concerns

---

## External Links (GitHub Web Viewing)

*The relative paths in this document are optimized for Claude Code. When viewing on GitHub, use these links to access other repositories:*

- [Orchestration Repository](https://github.com/budgetanalyzer/orchestration)
- [Orchestration CLAUDE.md](https://github.com/budgetanalyzer/orchestration/blob/main/CLAUDE.md)
