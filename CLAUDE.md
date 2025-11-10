# Service Common - Shared Library for Microservices

## Project Overview

The Service Common library is a shared Java library containing common code used across all microservices in the Budget Analyzer ecosystem. It provides standardized implementations for exception handling, API error responses, domain entities, CSV parsing utilities, and logging.

This library is published to Maven Local and consumed as a dependency by:
- **transaction-service** - Transaction and budget management service
- **currency-service** - Currency and exchange rate service
- Future microservices in the ecosystem

## Architecture

### Technology Stack

- **Language**: Java 24
- **Framework**: Spring Boot 3.5.6 (dependency management only - not a runnable application)
- **Build Tool**: Gradle (Kotlin DSL)
- **Code Quality**: Spotless (Google Java Format), Checkstyle
- **Distribution**: Maven Local publishing

**IMPORTANT**: This section should be kept in sync with [build.gradle.kts](build.gradle.kts). When version numbers or dependencies change in the build file, update this documentation accordingly.

### Package Structure

The library follows a clear modular structure organized into two main hierarchies:

#### Core Package (`org.budgetanalyzer.core`)

Domain-agnostic utilities and infrastructure components:

- **core/domain/** - Base JPA entities and listeners
  - `AuditableEntity` - Base entity with createdAt/updatedAt timestamps
  - `SoftDeletableEntity` - Extends AuditableEntity with soft-delete support
  - `SoftDeleteListener` - JPA lifecycle listener for soft-delete behavior

- **core/repository/** - Repository abstractions
  - `SoftDeleteOperations` - Interface for soft-delete repository methods

- **core/csv/** - CSV parsing utilities
  - `CsvParser` - Interface for CSV parsing
  - `CsvData` - Represents parsed CSV with headers and rows
  - `CsvRow` - Single row with column access methods
  - `OpenCsvParser` - Implementation using OpenCSV library

- **core/logging/** - Logging utilities
  - `SafeLogger` - Logger that sanitizes sensitive data

#### Service Package (`org.budgetanalyzer.service`)

Spring Boot service-specific components:

- **service/exception/** - Standard exception hierarchy
  - `ServiceException` - Base exception (500 Internal Server Error)
  - `ClientException` - Client error base (400 Bad Request)
  - `ResourceNotFoundException` - Entity not found (404 Not Found)
  - `InvalidRequestException` - Malformed request (400 Bad Request)
  - `BusinessException` - Business rule violation (422 Unprocessable Entity)
  - `ServiceUnavailableException` - Service unavailable (503 Service Unavailable)

- **service/api/** - API response standards
  - `ApiErrorResponse` - Standard error response format with type, message, code, and fieldErrors
  - `FieldError` - Field-level validation error
  - `ApiErrorType` - Error type enumeration (INVALID_REQUEST, VALIDATION_ERROR, NOT_FOUND, APPLICATION_ERROR, SERVICE_UNAVAILABLE, INTERNAL_ERROR)
  - `DefaultApiExceptionHandler` - Global exception handler (@RestControllerAdvice)

- **service/config/** - Configuration classes
  - `BaseOpenApiConfig` - Base OpenAPI/Swagger configuration

- **service/http/** - HTTP request/response logging infrastructure
  - `CorrelationIdFilter` - Generates/extracts correlation IDs for distributed tracing
  - `HttpLoggingFilter` - Logs HTTP requests and responses with sensitive data masking
  - `HttpLoggingProperties` - Configuration properties for HTTP logging
  - `HttpLoggingConfig` - Auto-configuration for HTTP logging filters
  - `ContentLoggingUtil` - Utility for extracting and formatting HTTP content

**Package Dependency Rules:**
```
service → core (service layer can use core utilities)
core → (no dependencies on service layer - domain-agnostic)
```

**IMPORTANT**: This section should be kept in sync code changes. When we add new 
packages and files, update this documentation accordingly.

## Architectural Principles

### 1. Production-Quality Code

**RULE**: All code must be production-ready. No shortcuts, prototypes, or workarounds.

- Follow established design patterns
- Implement proper error handling and validation
- Write comprehensive tests
- Ensure thread safety where applicable
- Use appropriate logging levels
- Handle edge cases explicitly

### 2. Persistence Layer: Pure JPA

**RULE**: Use pure JPA (Jakarta Persistence API) exclusively. NO Hibernate-specific features.

**Why?**
- **Portability**: Allows switching JPA providers without code changes
- **Standard compliance**: JPA is a specification with multiple implementations
- **Architectural discipline**: Maintains flexibility at minimal cost

**Forbidden:**
```java
❌ import org.hibernate.*;
❌ import org.hibernate.annotations.*;
❌ import org.hibernate.criterion.*;
```

**Allowed:**
```java
✅ import jakarta.persistence.*;
```

**Note**: While we acknowledge Hibernate is unlikely to be replaced, adhering to JPA standards is a best practice that prevents vendor lock-in and maintains architectural integrity.

### 3. Standardized Error Response Format

**RULE**: All API error responses follow a consistent, standardized format.

The `ApiErrorResponse` class provides a standardized format for all error responses across microservices:

```json
{
  "type": "VALIDATION_ERROR",
  "message": "One or more fields have validation errors",
  "fieldErrors": [
    {
      "field": "amount",
      "rejectedValue": "-100",
      "message": "Amount must be positive"
    }
  ]
}
```

**Error Response Fields:**
- `type` - Error type enumeration (see `ApiErrorType`)
- `message` - Human-readable error description
- `code` - Optional machine-readable error code (for `APPLICATION_ERROR` type)
- `fieldErrors` - Optional array of field-level validation errors (for `VALIDATION_ERROR` type)

**Error Types (`ApiErrorType` enum):**
- `INVALID_REQUEST` - Malformed request or bad syntax
- `VALIDATION_ERROR` - Field validation failed (includes `fieldErrors` array)
- `NOT_FOUND` - Requested resource does not exist
- `APPLICATION_ERROR` - Business rule violation (includes `code` field)
- `SERVICE_UNAVAILABLE` - Downstream service unavailable
- `INTERNAL_ERROR` - Unexpected server error (HTTP 500)

**Benefits:**
- Consistent error format across all microservices
- Machine-readable error types
- Field-level validation error details
- Application-specific error codes for business logic errors
- Easy to parse on frontend

### 4. Soft Delete Pattern

**Implementation**: The `SoftDeletableEntity` base class provides automatic soft-delete functionality.

**How It Works:**
1. Extend `SoftDeletableEntity` in your entity classes
2. Entity has `deleted` boolean field (default: false)
3. `SoftDeleteListener` JPA lifecycle listener intercepts deletions
4. On delete, sets `deleted=true` instead of removing from database
5. Queries can filter via `findByIdActive()` or `findAllActive()` methods

**Benefits:**
- Data retention for audit and compliance
- Ability to restore deleted records
- Historical data preservation
- No foreign key violations on deletion

**Example Usage:**
```java
@Entity
public class Transaction extends SoftDeletableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;
    // ... other fields
}

// In repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>,
                                               SoftDeleteOperations<Transaction, Long> {
    // Automatically gets findByIdActive(), findAllActive() from SoftDeleteOperations
}

// In controller
transactionRepository.findByIdActive(id)
    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
```

### 5. Auditable Entities

**Implementation**: The `AuditableEntity` base class provides automatic timestamp tracking.

**Features:**
- `createdAt` - Timestamp when entity was created (immutable)
- `updatedAt` - Timestamp when entity was last modified (auto-updated)
- Managed by JPA lifecycle callbacks (`@PrePersist`, `@PreUpdate`)

**Note**: `SoftDeletableEntity` extends `AuditableEntity`, so soft-deletable entities automatically get timestamp tracking.

### 6. Global Exception Handling

**Implementation**: `DefaultApiExceptionHandler` is a `@RestControllerAdvice` that intercepts all exceptions.

**Exception Mapping:**

| Exception | HTTP Status | Error Type | Use Case |
|-----------|-------------|------------|----------|
| `ResourceNotFoundException` | 404 | `NOT_FOUND` | Entity not found by ID |
| `InvalidRequestException` | 400 | `INVALID_REQUEST` | Malformed request data |
| `BusinessException` | 422 | `APPLICATION_ERROR` | Business rule violation (includes error code) |
| `ServiceUnavailableException` | 503 | `SERVICE_UNAVAILABLE` | External service failure |
| `ServiceException` | 500 | `INTERNAL_ERROR` | Unexpected server error |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` | Bean validation failure (includes fieldErrors) |
| Generic `Exception` | 500 | `INTERNAL_ERROR` | Unhandled exceptions |

**Consuming Services:**
- Import this library as a dependency
- Component scanning will auto-discover `DefaultApiExceptionHandler`
- All exceptions automatically converted to `ApiErrorResponse` format
- Consistent error handling across all microservices

### 7. Code Quality Standards

**Spotless Configuration:**
- Google Java Format (1.17.0)
- Automatic import ordering: java → javax → jakarta → org → com → org.budgetanalyzer
- Trailing whitespace removal
- File ends with newline
- Unused import removal

**Checkstyle Enforcement:**
- Version 12.0.1
- Custom rules in `config/checkstyle/checkstyle.xml`
- Enforces Hibernate import ban
- Enforces naming conventions

**Variable Declarations:**
   **Use `var` whenever possible** for local variables to reduce verbosity and improve readability.
      - Prefer `var` whenever possible
      - Use explicit types only when the only other option is to cast a return type, e.g. 
      ```java
         Map<String, Object> details = Map.of("method", "POST", "uri", "/api/users", "status", 201);
         var body = "{\"name\":\"John Doe\"}";
      ```
**Imports:**
  **No wildcard imports, always expand explicit imports**

**Javadoc Comments:**
  **CRITICAL**: All Javadoc comments must follow these formatting rules to pass Checkstyle:

  - **First sentence MUST end with a period (`.`)** - This is enforced by the `SummaryJavadoc` Checkstyle rule
  - The first sentence should be a concise summary (appears in method/class listings)
  - Use proper punctuation throughout

  **Examples:**

  ```java
  // ✅ CORRECT - First sentence ends with period
  /** Converts object to JSON string with sensitive fields masked. */
  public static String toJson(Object object) { }

  /** Header name for correlation ID. */
  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

  /**
   * Masks a sensitive string value.
   *
   * @param value The value to mask
   * @param showLast Number of characters to show at the end
   * @return Masked value
   */
  public static String mask(String value, int showLast) { }

  // ❌ INCORRECT - Missing period at end of first sentence
  /** Converts object to JSON string with sensitive fields masked */
  public static String toJson(Object object) { }

  /** Header name for correlation ID */
  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
  ```

  **Key Points:**
  - Single-line Javadoc: `/** Summary sentence here. */`
  - Multi-line Javadoc: First line after `/**` must end with period
  - Field documentation: Even short descriptions need periods
  - Always end the summary sentence with a period, even if it's obvious

**Build Commands:**

**IMPORTANT**: Always use these two commands in sequence. Never use other gradle commands like `check`, `bootJar`, `checkstyleMain`, etc.

```bash
# 1. Format code (always run first)
./gradlew spotlessApply

# 2. Build and test (always run second)
./gradlew clean build
```

The `clean build` command will:
- Clean previous build artifacts
- Compile all source code
- Run Spotless checks
- Run Checkstyle
- Run all unit tests
- Build the JAR file
- Generate sources JAR
- Generate Javadoc JAR

## Library Features

### Exception Hierarchy

**Base Exceptions:**
- `ServiceException` - Base for all service exceptions (500)
- `ClientException` - Base for client errors (400)

**Specific Exceptions:**
- `ResourceNotFoundException` - Entity not found (404)
- `InvalidRequestException` - Bad request data (400)
- `BusinessException` - Business rule violation (422)
- `ServiceUnavailableException` - Service unavailable (503)

**Usage in Controllers:**
```java
@GetMapping("/{id}")
public TransactionResponse getById(@PathVariable Long id) {
    var transaction = repository.findByIdActive(id)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
    return mapper.toResponse(transaction);
}
```

**Usage in Services:**
```java
@Service
public class TransactionServiceImpl implements TransactionService {
    public Transaction createTransaction(Transaction transaction) {
        // Business validation
        if (transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Transaction amount must be positive");
        }

        // Check for duplicates
        if (isDuplicate(transaction)) {
            throw new BusinessException("Duplicate transaction detected");
        }

        return transactionRepository.save(transaction);
    }
}
```

### CSV Parsing Utilities

**CsvParser Interface:**
Abstraction for parsing CSV files with multiple implementations.

**OpenCsvParser Implementation:**
Uses OpenCSV library (version 3.7) for robust CSV parsing.

**Usage Example:**
```java
@Service
public class CsvImportService {
    private final CsvParser csvParser;

    public CsvImportService(CsvParser csvParser) {
        this.csvParser = csvParser;
    }

    public List<Transaction> importCsv(MultipartFile file) throws IOException {
        CsvData csvData = csvParser.parse(file.getInputStream());

        List<Transaction> transactions = new ArrayList<>();
        for (CsvRow row : csvData.getRows()) {
            String dateStr = row.get("Transaction Date");
            String amountStr = row.get("Amount");
            String description = row.get("Description");

            Transaction transaction = new Transaction();
            transaction.setDate(LocalDate.parse(dateStr, formatter));
            transaction.setAmount(new BigDecimal(amountStr));
            transaction.setDescription(description);

            transactions.add(transaction);
        }

        return transactions;
    }
}
```

**Features:**
- Header-based column access (e.g., `row.get("Amount")`)
- Automatic header parsing
- Row-by-row iteration
- Exception handling for parsing errors

### Safe Logging

**SafeLogger Utility:**
Provides logging with automatic sanitization of sensitive data.

**Purpose:**
- Prevent accidentally logging passwords, tokens, API keys
- Redact sensitive fields in logs
- Maintain compliance with security standards

**Usage:**
```java
public class UserService {
    private static final SafeLogger logger = SafeLogger.getLogger(UserService.class);

    public void updateUser(User user) {
        // Sensitive data automatically redacted
        logger.info("Updating user: {}", user); // password field redacted

        userRepository.save(user);
    }
}
```

### HTTP Request/Response Logging

**Production-ready HTTP logging infrastructure** for debugging, audit, and distributed tracing.

**Key Features:**
- **Correlation ID tracking** - Unique ID for each request, propagated across services
- **Request/response logging** - Method, URI, headers, query params, body
- **Sensitive data masking** - Automatic redaction of Authorization, Cookie, API keys
- **Configurable filtering** - Include/exclude patterns, body size limits, error-only logging
- **Performance optimized** - Content caching, conditional logging, minimal overhead
- **MDC integration** - Correlation ID available in all log entries

**Components:**
1. **CorrelationIdFilter** - Always enabled, generates/extracts correlation ID
2. **HttpLoggingFilter** - Conditionally enabled, logs request/response with sanitization
3. **HttpLoggingProperties** - Type-safe configuration via application.yml
4. **ContentLoggingUtil** - Content extraction and formatting utilities

**Auto-Configuration:**
Filters are automatically discovered via Spring Boot component scanning. No manual registration required.

**Configuration Example (application.yml):**
```yaml
budgetanalyzer:
  service:
    http-logging:
      enabled: true                    # Enable HTTP logging (default: false)
      log-level: DEBUG                 # Log level: TRACE, DEBUG, INFO, WARN, ERROR
      include-request-body: true       # Log request body
      include-response-body: true      # Log response body
      include-request-headers: true    # Log request headers
      include-response-headers: true   # Log response headers
      include-query-params: true       # Log query parameters
      include-client-ip: true          # Log client IP address
      max-body-size: 10000             # Max body size in bytes (10KB default)
      log-errors-only: false           # Log only 4xx/5xx responses
      exclude-patterns:                # Skip logging for these paths
        - /actuator/**
        - /swagger-ui/**
        - /v3/api-docs/**
      include-patterns:                # Explicitly include (overrides exclude)
        - /api/**
      sensitive-headers:               # Headers to mask (case-insensitive, defaults shown)
        - Authorization
        - Cookie
        - Set-Cookie
        - X-API-Key
        - X-Auth-Token
        - Proxy-Authorization
        - WWW-Authenticate
```

**Logged Information:**

*Request Logging:*
- HTTP method and URI
- Query parameters (if enabled)
- Request headers (sensitive headers masked)
- Request body (size-limited, JSON sanitized via SafeLogger)
- Client IP (checks X-Forwarded-For and proxy headers)
- Correlation ID (from MDC)

*Response Logging:*
- HTTP status code
- Response headers (sensitive headers masked)
- Response body (size-limited, JSON sanitized)
- Processing duration in milliseconds
- Correlation ID (from MDC)

**Log Level Strategy:**
- Requests: Logged at configured level (default: DEBUG)
- 2xx/3xx responses: Logged at configured level
- 4xx responses: Logged at WARN level
- 5xx responses: Logged at ERROR level

**Correlation ID Format:**
- Format: `req_<16-hex-chars>` (e.g., `req_a1b2c3d4e5f6g7h8`)
- Extracted from `X-Correlation-ID` request header if present
- Auto-generated if not provided
- Added to `X-Correlation-ID` response header
- Available in MDC as `correlationId` key

**Integration with SafeLogger:**
- Request/response bodies are sanitized using SafeLogger's JSON serialization
- Fields annotated with `@Sensitive` are automatically masked
- Custom masking patterns supported

**Performance Considerations:**
- Uses Spring's `ContentCachingRequestWrapper` and `ContentCachingResponseWrapper`
- Body size limits prevent logging huge payloads
- Path-based filtering skips unnecessary logging
- Minimal overhead when disabled or excluded

**Usage Example - Logback Configuration (logback.xml):**
```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level %logger{36} [correlationId=%X{correlationId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="org.budgetanalyzer.service.http" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

**Distributed Tracing:**
The correlation ID can be propagated to downstream services:
```java
@Service
public class CurrencyClient {
    public ExchangeRate fetchRate(String currencyPair) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);

        // Add to outgoing request
        return webClient.get()
            .uri("/exchange-rates/{pair}", currencyPair)
            .header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId)
            .retrieve()
            .bodyToMono(ExchangeRate.class)
            .block();
    }
}
```

### OpenAPI Base Configuration

**BaseOpenApiConfig:**
Provides common OpenAPI/Swagger configuration for all microservices.

**Features:**
- Standardized API documentation structure
- Common security schemes (future)
- Consistent endpoint grouping
- API versioning support

**Usage in Microservices:**
```java
@Configuration
public class OpenApiConfig extends BaseOpenApiConfig {
    @Override
    protected String getApiTitle() {
        return "Budget Analyzer API";
    }

    @Override
    protected String getApiDescription() {
        return "API for managing financial transactions and budgets";
    }

    @Override
    protected String getApiVersion() {
        return "1.0.0";
    }
}
```

## Publishing and Consumption

### Publishing to Maven Local

**Build and Publish:**
```bash
./gradlew spotlessApply
./gradlew clean build
./gradlew publishToMavenLocal
```

**Published Artifacts:**
- Main JAR: `service-common-0.0.1-SNAPSHOT.jar`
- Sources JAR: `service-common-0.0.1-SNAPSHOT-sources.jar`
- Javadoc JAR: `service-common-0.0.1-SNAPSHOT-javadoc.jar`

**Maven Coordinates:**
```groovy
groupId: org.budgetanalyzer
artifactId: service-common
version: 0.0.1-SNAPSHOT
```

### Consuming in Microservices

**Add Dependency (build.gradle.kts):**
```kotlin
dependencies {
    implementation("org.budgetanalyzer:service-common:0.0.1-SNAPSHOT")
}
```

**Enable Component Scanning:**
```java
@SpringBootApplication(scanBasePackages = {
    "org.budgetanalyzer.budgetanalyzer",  // Your service package
    "org.budgetanalyzer.service"           // Service-common package
})
public class BudgetAnalyzerApplication {
    public static void main(String[] args) {
        SpringApplication.run(BudgetAnalyzerApplication.class, args);
    }
}
```

**Benefits:**
- Global exception handler auto-discovered
- Base entities available for extension
- Common utilities accessible everywhere
- Consistent error responses across all services

## Development Workflow

### Local Development

1. **Prerequisites:**
   - JDK 24
   - Gradle 8.11+

2. **Build Library:**
   ```bash
   ./gradlew spotlessApply
   ./gradlew clean build
   ```

3. **Publish to Maven Local:**
   ```bash
   ./gradlew publishToMavenLocal
   ```

4. **Use in Microservices:**
   Consuming services will automatically pick up the latest version from Maven Local.

### Code Formatting

**Before committing:**
```bash
./gradlew spotlessApply
./gradlew clean build
```

### Git Workflow

- Create feature branches from `main`
- Follow conventional commits
- Run all checks before pushing
- Request code review for all changes

## Testing Strategy

### Unit Tests

**Current Coverage:**
- Limited test coverage (opportunity for improvement)
- Focus on critical utilities (CSV parsing, exception handling)

**Test Framework:**
- JUnit 5 (Jupiter)
- Spring Boot Test (for integration testing if needed)

**Running Tests:**
```bash
./gradlew test
```

### Testing Guidelines

**What to Test:**
- CSV parsing with various formats
- Exception handler response formatting
- Soft-delete lifecycle behavior
- Auditable entity timestamp management
- Safe logger sanitization logic

**Test Coverage Goals:**
- Minimum 80% code coverage
- 100% coverage for critical utilities
- All edge cases explicitly tested

### Testing Philosophy: Test Correct Behavior, Not Defects

**CRITICAL PRINCIPLE**: Tests must always be written for how components **should** behave according to their specification and real-world requirements, NOT around defective implementations.

**When Tests Fail Due to Implementation Issues:**

1. **STOP immediately** - Do not attempt to work around the implementation deficiency
2. **Analyze the failure** - Determine if the test is correct or if the implementation is deficient
3. **Explain the situation** to the user:
   - What behavior the test expects (correct behavior)
   - What the current implementation does (deficient behavior)
   - Why the implementation is inadequate
4. **Ask the user** how to proceed:
   - Option A: Fix the implementation to match correct behavior
   - Option B: Remove/modify tests if requirements have changed

**Example Scenario:**

```
User: Add tests for CSV parsing with mismatched column counts

Assistant discovers:
- Tests written for correct behavior (handle rows with fewer/more columns than headers)
- Implementation fails because it assumes all rows have same column count as headers
- This is a common real-world edge case that MUST be handled

Assistant response:
"I've written tests for handling mismatched column counts (a common real-world scenario),
but they're failing because the current OpenCsvParser implementation doesn't handle this
edge case. The implementation assumes all rows have exactly the same number of columns
as headers, which causes ArrayIndexOutOfBoundsException.

Would you like me to:
1. Fix the implementation to handle mismatched columns properly, or
2. Remove these tests (not recommended - this is a real-world edge case)"
```

**Why This Matters:**

- **Test integrity**: Tests document correct behavior and serve as specifications
- **Code quality**: Working around bugs creates technical debt
- **Maintainability**: Future developers need accurate tests, not workarounds
- **Real-world robustness**: Edge cases are often discovered in production if not tested

**Anti-Pattern to Avoid:**

```java
// ❌ WRONG - Writing tests around broken implementation
@Test
void shouldThrowExceptionWhenRowHasFewerColumns() {
    // Documenting current buggy behavior instead of fixing it
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
        parser.parse("Name,Age\nJohn");
    });
}
```

**Correct Pattern:**

```java
// ✅ CORRECT - Writing test for correct behavior
@Test
void shouldHandleRowsWithFewerColumnsThanHeaders() {
    var result = parser.parse("Name,Age\nJohn");
    // Correct behavior: missing columns should be empty strings
    assertEquals("", result.get(0).get("Age"));
}
// If this fails, FIX THE IMPLEMENTATION, don't change the test
```

## Best Practices

### General

1. **Follow SOLID principles**: Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion
2. **Favor composition over inheritance**
3. **Program to interfaces, not implementations**
4. **Use dependency injection** for all dependencies
5. **Avoid static methods** except for pure utility functions
6. **Immutability**: Use final fields where possible
7. **Null safety**: Use Optional for potentially null returns

### Library Design

1. **Backward compatibility**: Never break existing APIs without major version bump
2. **Minimal dependencies**: Only include essential dependencies to avoid conflicts
3. **Clear package separation**: Core vs. service packages
4. **Comprehensive Javadoc**: All public APIs must be documented
5. **Semantic versioning**: Follow semver for releases

### Spring Boot Integration

1. **Component scanning friendly**: Use standard Spring annotations
2. **Auto-configuration support**: Provide sensible defaults
3. **Configuration properties**: Use `@ConfigurationProperties` for complex config
4. **Conditional beans**: Use `@ConditionalOnProperty` for optional features

## Common Tasks

### Adding a New Base Entity

1. Create entity class in `core/domain/` package
2. Extend `AuditableEntity` or `SoftDeletableEntity` as appropriate
3. Add JPA annotations (`@MappedSuperclass` for base entities)
4. Document usage in Javadoc
5. Write unit tests for lifecycle behavior
6. Update this documentation
7. Publish new version

### Adding a New Exception Type

1. Create exception class in `service/exception/` package
2. Extend `ServiceException` or `ClientException` as appropriate
3. Add constructor with message and optional cause
4. Update `DefaultApiExceptionHandler` to map exception to HTTP status
5. Add error type to `ApiErrorType` enum
6. Document usage examples
7. Write unit tests
8. Update this documentation

### Adding a New Utility Class

1. Determine if it belongs in `core` (domain-agnostic) or `service` (Spring-specific)
2. Create class in appropriate package
3. Make methods static if stateless, or use Spring bean if stateful
4. Add comprehensive Javadoc with usage examples
5. Write unit tests with edge cases
6. Update this documentation

### Publishing a New Version

1. **Update Version**: Edit `build.gradle.kts` version field
2. **Document Changes**: Update changelog or release notes
3. **Build and Test**:
   ```bash
   ./gradlew spotlessApply
   ./gradlew clean build
   ```
4. **Publish**:
   ```bash
   ./gradlew publishToMavenLocal
   ```
5. **Tag Release**:
   ```bash
   git tag -a v0.0.2 -m "Release version 0.0.2"
   git push origin v0.0.2
   ```
6. **Update Consuming Services**: Update dependency versions in microservices

## Troubleshooting

### Build Failures

**Spotless formatting violations:**
```bash
./gradlew spotlessApply
```

**Checkstyle errors:**
Review `config/checkstyle/checkstyle.xml` rules and fix violations including warnings if possible.

**Test failures:**
```bash
./gradlew cleanTest test
```

### Publishing Issues

**Maven Local not found by consuming service:**
- Ensure `publishToMavenLocal` completed successfully
- Check `~/.m2/repository/org/budgetanalyzer/service-common/` exists
- Verify version matches in consuming service's `build.gradle.kts`

**Component scanning not finding beans:**
- Ensure `scanBasePackages` includes `org.budgetanalyzer.service`
- Check Spring Boot version compatibility (requires 3.x)

## Notes for Claude Code

When working on this project:

### Critical Rules

1. **NEVER implement changes without explicit permission** - Always present a plan and wait for approval
2. **Distinguish between informational statements and action requests** - If the user says "I did X", they're informing you, not asking you to do it
3. **Questions deserve answers, not implementations** - Respond to questions with information, not code changes
4. **Wait for explicit implementation requests** - Only implement when the user says "implement", "do it", "make this change", or similar action-oriented language
5. **Limit file access to the current directory and below** - Don't read or write files outside of the current service-common directory

### Code Quality

- **All code must be production-quality** - No shortcuts, prototypes, or workarounds
- **Use pure JPA only** - No Hibernate-specific imports or annotations
- **Maintain package separation** - Clear boundaries between core and service packages
- **Always run these commands before committing:**
  1. `./gradlew spotlessApply` - Format code
  2. `./gradlew clean build` - Build and test everything

### Checkstyle Warning Handling

**CRITICAL**: When verifying the build with `./gradlew clean build`, always pay attention to Checkstyle warnings.

**Required Actions:**
1. **Always read build output carefully** - Look for Checkstyle warnings even if the build succeeds
2. **Attempt to fix all Checkstyle warnings** - Treat warnings as errors that need immediate resolution
3. **Common Checkstyle issues to watch for:**
   - Javadoc missing periods at end of first sentence
   - Missing Javadoc comments on public methods/classes
   - Import statement violations (wildcard imports, Hibernate imports)
   - Line length violations
   - Naming convention violations
   - Indentation issues
4. **If unable to fix warnings:**
   - Document the specific warning message
   - Explain why it cannot be fixed
   - Notify the user immediately with the warning details
   - Provide the file path and line number where the warning occurs
5. **Never ignore warnings** - Even if the build passes, Checkstyle warnings indicate code quality issues that must be addressed

**Example Response Pattern:**
```
Build completed successfully, but found Checkstyle warnings:
- File: src/main/java/org/budgetanalyzer/service/Example.java:42
- Issue: Javadoc comment missing period at end of first sentence
- Action: Fixed by adding period to Javadoc summary

OR

Build completed with Checkstyle warnings that I cannot resolve:
- File: src/main/java/org/budgetanalyzer/service/Example.java:42
- Warning: [specific warning message]
- Reason: [explanation of why it cannot be fixed]
```

### Library Design Principles

- **Backward compatibility** - Never break existing APIs without discussion
- **Minimal dependencies** - Only add essential dependencies
- **Comprehensive documentation** - All public APIs need Javadoc
- **Semantic versioning** - Follow semver for version changes

### Testing Requirements

- Write tests for all new features
- Maintain minimum 80% coverage
- Test edge cases explicitly
- Use proper test doubles (mocks, stubs, fakes)

### Documentation

- Update this file when architecture changes
- Add Javadoc for public APIs
- Document complex utilities with examples
- Maintain changelog for version releases

## Future Enhancements

### Planned 📋

#### High Priority - Library Features
- [ ] **Add MapStruct integration** - Provide base mapper interfaces for DTO transformations
- [ ] **Add validation utilities** - Common validation annotations and validators
- [ ] **Add pagination support** - Standard `Page` and `Pageable` response wrappers
- [ ] **Add audit logging interceptor** - Automatic logging of entity changes
- [x] **Add request correlation ID filter** - Track requests across microservices
  - ✅ Completed: Implemented comprehensive HTTP logging infrastructure including:
    - `CorrelationIdFilter` - Generates/extracts unique correlation IDs for distributed tracing
    - `HttpLoggingFilter` - Logs HTTP requests and responses with sensitive data masking
    - `HttpLoggingProperties` - Type-safe configuration via application.yml
    - `HttpLoggingConfig` - Auto-configuration for filter registration
    - `ContentLoggingUtil` - Utilities for content extraction and formatting
    - Integration with SafeLogger for automatic JSON sanitization
    - MDC integration for correlation ID propagation in logs
    - Configurable include/exclude patterns, body size limits, and log levels
    - See "HTTP Request/Response Logging" section for full documentation

#### High Priority - Testing
- [x] **Add comprehensive unit tests** - Test all utilities, exceptions, and base entities
  - ✅ Completed: Implemented 24 test classes covering all major components:
    - Core domain tests: `AuditableEntityTest`, `SoftDeletableEntityTest`, `SoftDeleteListenerTest`, `SoftDeleteOperationsTest`
    - CSV parsing tests: `CsvDataTest`, `CsvRowTest`, `OpenCsvParserTest`
    - Logging tests: `SafeLoggerTest`, `SensitiveDataModuleTest`
    - API/Exception tests: `ApiErrorResponseTest`, `ApiErrorTypeTest`, `FieldErrorTest`, `DefaultApiExceptionHandlerTest`
    - Exception hierarchy tests: `ServiceExceptionTest`, `ClientExceptionTest`, `BusinessExceptionTest`, `ResourceNotFoundExceptionTest`, `InvalidRequestExceptionTest`, `ServiceUnavailableExceptionTest`
    - HTTP logging tests: `CorrelationIdFilterTest`, `HttpLoggingFilterTest`, `HttpLoggingPropertiesTest`, `HttpLoggingConfigTest`, `ContentLoggingUtilTest`
    - Configuration tests: `BaseOpenApiConfigTest`
    - All 26 main classes have test coverage (including interface/annotation testing through implementations)
- [ ] **Add integration tests** - Test Spring Boot integration and component scanning
- [ ] **Add example usage tests** - Demonstrate how to use each feature

#### Medium Priority - Utilities
- [ ] **Add JSON utilities** - Common JSON serialization/deserialization helpers
- [ ] **Add date/time utilities** - Standard date formatting and timezone handling
- [ ] **Add currency utilities** - ISO 4217 currency code validation
- [ ] **Add HTTP client utilities** - Standardized RestTemplate/WebClient configuration

#### Medium Priority - Documentation
- [ ] **Add usage examples** - Create example microservice showing all features
- [ ] **Add migration guide** - Document how to migrate existing services to use this library
- [ ] **Add contribution guide** - Guidelines for adding new features

#### Low Priority - Optional Features
- [ ] **Add metrics support** - Standard Micrometer metrics for common operations
- [ ] **Add distributed tracing** - OpenTelemetry integration for tracing
- [ ] **Add event publishing support** - Base event classes for domain events
- [ ] **Add security utilities** - Common security filters and authentication helpers

## Support and Contact

For questions or issues:
- Review this documentation first
- Check existing GitHub issues
- Create new issue with detailed description and reproduction steps
