# Service-Common - Shared Spring Boot Library

## Purpose

Shared library for all Budget Analyzer Spring Boot microservices. Provides standardized implementations for exception handling, API error responses, base entities, CSV parsing, logging, and HTTP request/response logging.

**Impacts**: transaction-service, currency-service, and all future Spring Boot services.
Changes here affect all services that depend on this library.

## When to Use This Library

- ✅ Cross-service utilities (logging, error handling, common DTOs)
- ✅ Shared Spring Boot configurations
- ✅ Common dependencies and version management
- ❌ Service-specific business logic (belongs in service repos)

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
- Services: `*Service` interface + `*ServiceImpl` implementation + `@Transactional` at this layer
- Repositories: `*Repository` extends `JpaRepository<Entity, ID>`
- Pure JPA only: **Forbidden** `org.hibernate.*` → **Use** `jakarta.persistence.*`
- Base entities: Extend `AuditableEntity` (timestamps) or `SoftDeletableEntity` (soft delete)

**For comprehensive patterns, read [docs/spring-boot-conventions.md](docs/spring-boot-conventions.md) when working on architecture tasks.**

**Discovery**:
```bash
# View package structure
find src/main/java -type d | grep -E "org/budgetanalyzer" | head -20

# Find all base entities
grep -r "@MappedSuperclass" src/
```

## Exception Handling

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
- All exceptions auto-converted to `ApiErrorResponse` by `DefaultApiExceptionHandler`

**For complete error handling patterns, read [docs/error-handling.md](docs/error-handling.md) when implementing error flows.**

**Discovery**:
```bash
# Find all custom exceptions
grep -r "extends.*Exception" src/ | grep -v "Test"

# View exception handler
cat src/main/java/org/budgetanalyzer/service/api/DefaultApiExceptionHandler.java
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
# View test structure
find src/test/java -name "*Test.java" | head -10

# Run tests with coverage
./gradlew test jacocoTestReport
```

## Key Features

### Base Entities
- `AuditableEntity` - Automatic `createdAt`/`updatedAt` timestamps
- `SoftDeletableEntity` - Soft-delete support with `deleted` flag

### Exception Hierarchy
- Standard exceptions: `ResourceNotFoundException`, `BusinessException`, `InvalidRequestException`
- Global exception handler with standardized error responses

### HTTP Logging Infrastructure
- `CorrelationIdFilter` - Distributed tracing with correlation IDs
- `HttpLoggingFilter` - Request/response logging with sensitive data masking
- Configurable via `application.yml` properties

### Utilities
- CSV parsing (`CsvParser`, `OpenCsvParser`)
- Safe logging with automatic sensitive data redaction (`SafeLogger`)
- OpenAPI base configuration (`BaseOpenApiConfig`)

**Discovery**:
```bash
# View all utilities
find src/main/java -name "*.java" -path "*/org/budgetanalyzer/core/*"

# View HTTP logging configuration
grep -r "HttpLogging" src/main/java
```

## Publishing and Consumption

### Publish to Maven Local
```bash
# Build and publish
./gradlew spotlessApply
./gradlew clean build
./gradlew publishToMavenLocal
```

**Maven Coordinates**:
```groovy
groupId: org.budgetanalyzer
artifactId: service-common
version: 0.0.1-SNAPSHOT
```

### Consume in Microservices
```kotlin
// build.gradle.kts
dependencies {
    implementation("org.budgetanalyzer:service-common:0.0.1-SNAPSHOT")
}
```

**Enable component scanning**:
```java
@SpringBootApplication(scanBasePackages = {
    "org.budgetanalyzer.{your-service}",  // Your service
    "org.budgetanalyzer.service"          // Service-common
})
```

## Architectural Principles

### 1. Production-Quality Code
All code must be production-ready. No shortcuts, prototypes, or workarounds.

**When to read more**: [docs/common-patterns.md](docs/common-patterns.md) - SOLID principles, design patterns, Spring Boot patterns, database patterns, security/performance best practices.

### 2. Pure JPA Only
**CRITICAL**: Use pure JPA (Jakarta Persistence API) exclusively. NO Hibernate-specific features.
- Forbidden: `org.hibernate.*`
- Allowed: `jakarta.persistence.*`

### 3. Standardized Error Responses
All API errors follow `ApiErrorResponse` format with error types, field-level validation, and error codes.

### 4. Soft Delete Pattern
Entities extending `SoftDeletableEntity` are never actually deleted from the database - only marked as deleted.

### 5. Code Quality Standards
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
./gradlew spotlessApply
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
1. **Backward compatibility** - Never break existing APIs without major version bump
2. **Minimal dependencies** - Only include essential dependencies
3. **Clear package separation** - Core (domain-agnostic) vs. Service (Spring-specific)
4. **Comprehensive Javadoc** - All public APIs must be documented

## Notes for Claude Code

### Critical Rules
1. **NEVER implement changes without explicit permission** - Always present a plan and wait for approval
2. **All code must be production-quality** - No shortcuts, prototypes, or workarounds
3. **Use pure JPA only** - No Hibernate-specific imports (`org.hibernate.*`)
4. **Always run build commands**:
   ```bash
   ./gradlew spotlessApply
   ./gradlew clean build
   ```
5. **Fix Checkstyle warnings** - Treat warnings as errors requiring immediate resolution

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
- System-wide architecture: budget-analyzer/orchestration CLAUDE.md (external repo - clone when needed)
- Individual service CLAUDE.md files - Service-specific concerns
