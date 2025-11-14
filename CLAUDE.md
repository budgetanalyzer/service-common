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

See [docs/spring-boot-conventions.md](docs/spring-boot-conventions.md) for:
- Architecture layers (Controller → Service → Repository)
- Naming conventions (`*Controller`, `*Service`, `*ServiceImpl`)
- Package structure patterns
- Base entity classes (`AuditableEntity`, `SoftDeletableEntity`)
- Pure JPA requirement (no Hibernate-specific code)

**Discovery**:
```bash
# View package structure
tree src/main/java/org/budgetanalyzer -L 2
# Or without tree: find src/main/java -type d | grep -E "org/budgetanalyzer" | head -20

# Find all base entities
grep -r "@MappedSuperclass" src/

# Check Spring Boot version
grep "springBootVersion" build.gradle.kts
```

## Exception Handling

**Pattern**: Centralized exception handling with standardized API error responses

See [docs/error-handling.md](docs/error-handling.md) for:
- Exception hierarchy (`ServiceException`, `ClientException`, etc.)
- `ApiErrorResponse` standard format
- `DefaultApiExceptionHandler` (@RestControllerAdvice)
- Usage examples and best practices

**Discovery**:
```bash
# Find all custom exceptions
grep -r "extends.*Exception" src/ | grep -v "Test"

# View exception handler
cat src/main/java/org/budgetanalyzer/service/api/DefaultApiExceptionHandler.java
```

## Testing Patterns

See [docs/testing-patterns.md](docs/testing-patterns.md) for:
- Testing philosophy (test correct behavior, not defects)
- Unit tests vs Integration tests
- TestContainers for PostgreSQL/Redis/RabbitMQ
- Mocking strategies
- Coverage goals (80% minimum)

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

**Build commands**:
```bash
# Always run these two commands in sequence
./gradlew spotlessApply
./gradlew clean build
```

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

**Detailed patterns documented in docs/**:
- [docs/spring-boot-conventions.md](docs/spring-boot-conventions.md) - Architecture, layers, naming, base entities
- [docs/error-handling.md](docs/error-handling.md) - Exception hierarchy, error responses
- [docs/testing-patterns.md](docs/testing-patterns.md) - Testing strategies, TestContainers, coverage

**See also**:
- [@orchestration/CLAUDE.md](https://github.com/budget-analyzer/orchestration/blob/main/CLAUDE.md) - System-wide architecture
- Individual service CLAUDE.md files - Service-specific concerns
