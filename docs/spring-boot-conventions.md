# Spring Boot Conventions - Budget Analyzer Microservices

## Service-Centric Architecture

**Pattern**: Modern Spring Boot layered architecture where services are first-class citizens and entities are data carriers.

```
Controller Layer (HTTP concerns)
    ↓ calls
Service Layer (business logic - the heart of the application)
    ↓ calls
Repository Layer (data access)
```

### Why Service-Centric?

This is a deliberate architectural choice for modern Spring Boot applications:

1. **Services as first-class citizens**: All business logic lives in `*Service` classes. When you need to understand or modify business rules, you know exactly where to look.

2. **Entities as data carriers**: JPA entities define structure and relationships, not behavior. This works naturally with JPA/Hibernate's proxy-based lazy loading and transaction boundaries.

3. **Proven at scale**: This pattern is battle-tested across thousands of production Spring Boot applications. It scales by adding services, not by bloating entities.

4. **Clear transaction boundaries**: `@Transactional` at the service layer gives predictable, testable transaction behavior.

5. **Testability**: Services are easy to unit test with mocked repositories. No complex entity lifecycle management in tests.

**Rules**:
- Controllers never call repositories directly
- Services contain all business logic
- Repositories are thin data access only
- Entities define data structure, not behavior

## Package Structure

**Standard package hierarchy**:
```
org.budgetanalyzer.{service-name}
├── api/               # REST controllers
│   ├── request/       # Request DTOs
│   └── response/      # Response DTOs
├── service/           # Business logic (concrete classes)
│   ├── dto/           # Service-layer DTOs
│   └── provider/      # External service provider interfaces & implementations
├── repository/        # JPA repositories
│   └── spec/          # JPA Specifications (optional)
├── domain/            # JPA entities
│   └── event/         # Domain events (optional)
├── client/            # External API clients
├── messaging/         # Messaging infrastructure (optional)
│   ├── listener/      # Message listeners
│   ├── publisher/     # Message publishers
│   ├── consumer/      # Message consumers
│   └── message/       # Message DTOs
├── scheduler/         # Scheduled tasks (optional)
└── config/            # Spring configuration classes
```

**Discovery**:
```bash
# Find all controllers in a service
grep -r "@RestController" src/

# Find all services
grep -r "@Service" src/

# Find all repositories
grep -r "@Repository" src/
```

## Naming Conventions

### Controllers
- **Pattern**: `*Controller`
- **Package**: `*.api`
- **Annotation**: `@RestController`
- **Example**: `TransactionController`, `BudgetController`
- **Request DTOs**: `*.api.request` package
- **Response DTOs**: `*.api.response` package

### Services
- **Pattern**: `*Service` (concrete classes, no interfaces)
- **Package**: `*.service`
- **Annotation**: `@Service`
- **Example**: `TransactionService`, `BudgetService`
- **Rule**: Internal business logic services are concrete classes only. See "When to Use Interfaces" below for the three patterns that DO require interfaces.

### When to Use Interfaces

Budget Analyzer uses interfaces in exactly THREE patterns:

#### 1. Provider Pattern (External Service Boundaries)
- **Rule**: 100% required for all external API/data source integrations
- **Pattern**: `*Provider` interface + concrete implementation
- **Package**: `*.service.provider`
- **Annotation**: `@Service` on implementation
- **Example**:
  - Interface: `ExchangeRateProvider`
  - Implementation: `FredExchangeRateProvider`, `EcbExchangeRateProvider`
- **Purpose**: Decouple from external services, allow multiple implementations
- **See**: [advanced-patterns.md](advanced-patterns.md#provider-abstraction-pattern) for detailed documentation

#### 2. Third-Party Library Abstraction (Avoiding Vendor Lock-in)
- **Rule**: Required when wrapping third-party libraries we don't control
- **Pattern**: Interface in `core.*` + implementation in `core.*.impl`
- **Annotation**: `@Component` on implementation
- **Example**:
  - Interface: `CsvParser` (in `org.budgetanalyzer.core.csv`)
  - Implementation: `OpenCsvParser` (in `org.budgetanalyzer.core.csv.impl`)
  - Third-party: `com.opencsv:opencsv`
- **Purpose**: Abstract third-party implementations to avoid lock-in, make switching libraries transparent to consuming code
- **Goal**: Make interfaces as generic as possible, independent of any specific library

#### 3. Repository Enhancement Pattern
- **Rule**: Used to extend Spring Data JPA with reusable query patterns
- **Pattern**: Interface with default methods extending `JpaSpecificationExecutor`
- **Example**: `SoftDeleteOperations<T extends SoftDeletableEntity>`
- **Purpose**: Add reusable query methods across all repositories (e.g., `findAllActive()`, `findByIdActive()`)

#### Anti-Pattern: Internal Service Interfaces
```java
// ❌ WRONG - Don't create interfaces for internal services
public interface TransactionService { }
public class TransactionServiceImpl implements TransactionService { }

// ✅ CORRECT - Internal services are concrete classes
@Service
public class TransactionService { }
```

### Repositories
- **Pattern**: `*Repository`
- **Package**: `*.repository`
- **Extends**: `JpaRepository<Entity, ID>`
- **Annotation**: `@Repository` (optional, inherited from JpaRepository)
- **Example**: `TransactionRepository`, `BudgetRepository`

### DTOs
- **Pattern**: `*Request`, `*Response` (API layer), `*DTO` (Service layer)
- **Packages**:
  - `*.api.request` - API request DTOs
  - `*.api.response` - API response DTOs
  - `*.service.dto` - Service-layer DTOs (internal)
- **Example**: `CreateTransactionRequest`, `TransactionResponse`, `TransactionDTO`

### Prefer Records for DTOs

**Rule**: Use Java records whenever possible for POJOs, especially request/response DTOs.

**Why records?**
- **Immutability**: Records are immutable by default - safer for concurrent code
- **Conciseness**: No boilerplate (constructors, getters, equals, hashCode, toString)
- **Clarity**: Clearly signals "this is just data"
- **Validation**: Works well with Bean Validation annotations

```java
// ✅ CORRECT - Use records for DTOs
public record CreateTransactionRequest(
    @NotBlank String description,
    @NotNull @Positive BigDecimal amount,
    @NotNull LocalDate date
) {}

public record TransactionResponse(
    Long id,
    String description,
    BigDecimal amount,
    LocalDate date,
    Instant createdAt
) {
    public static TransactionResponse from(Transaction entity) {
        return new TransactionResponse(
            entity.getId(),
            entity.getDescription(),
            entity.getAmount(),
            entity.getDate(),
            entity.getCreatedAt()
        );
    }
}

// Service-layer DTOs
public record EffectivePermissions(
    Set<String> rolePermissions,
    List<ResourcePermission> resourcePermissions
) {}

// ❌ AVOID - Traditional class for simple DTOs
public class CreateTransactionRequest {
    private String description;
    private BigDecimal amount;
    // ... getters, setters, equals, hashCode, toString
}
```

**When NOT to use records**:
- Entities (need mutability for JPA lifecycle)
- Classes requiring inheritance
- Classes needing mutable state after construction

### OpenAPI Documentation with SpringDoc

**Rule**: ALL request/response DTOs MUST be annotated with SpringDoc OpenAPI annotations for API documentation.

Budget Analyzer uses SpringDoc OpenAPI to generate interactive API documentation. This is fundamental to our design - the `BaseOpenApiConfig` in service-common provides base configuration, but all DTOs require proper annotations.

#### DTO Annotations

**Required imports**:
```java
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
```

**Request DTO Pattern** (with validation):
```java
public record CreateTransactionRequest(
    @Schema(
        description = "Transaction description",
        example = "Coffee at Starbucks",
        maxLength = 255,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank
    @Size(max = 255)
    String description,

    @Schema(
        description = "Transaction amount in the account's currency",
        example = "4.50",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
    @Positive
    BigDecimal amount,

    @Schema(
        description = "Date of the transaction",
        example = "2024-01-15",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
    LocalDate date,

    @Schema(
        description = "Optional category for the transaction",
        example = "Food & Dining",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    String category
) {}
```

**Response DTO Pattern**:
```java
public record TransactionResponse(
    @Schema(description = "Unique transaction identifier", example = "12345")
    Long id,

    @Schema(description = "Transaction description", example = "Coffee at Starbucks")
    String description,

    @Schema(description = "Transaction amount", example = "4.50")
    BigDecimal amount,

    @Schema(description = "Transaction date", example = "2024-01-15")
    LocalDate date,

    @Schema(
        description = "Transaction type",
        example = "EXPENSE",
        allowableValues = {"INCOME", "EXPENSE", "TRANSFER"}
    )
    String type,

    @Schema(description = "When the record was created", example = "2024-01-15T10:30:00Z")
    Instant createdAt,

    @Schema(
        description = "Category name, if assigned",
        example = "Food & Dining",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    String category
) {
    public static TransactionResponse from(Transaction entity) {
        return new TransactionResponse(
            entity.getId(),
            entity.getDescription(),
            entity.getAmount(),
            entity.getDate(),
            entity.getType().name(),
            entity.getCreatedAt(),
            entity.getCategory() != null ? entity.getCategory().getName() : null
        );
    }
}
```

**Filter/Query DTO Pattern** (all fields optional):
```java
public record TransactionFilter(
    @Schema(description = "Filter by description (partial match)", example = "coffee")
    String description,

    @Schema(description = "Filter transactions from this date", example = "2024-01-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate dateFrom,

    @Schema(description = "Filter transactions to this date", example = "2024-01-31")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate dateTo,

    @Schema(description = "Minimum transaction amount", example = "10.00")
    BigDecimal minAmount,

    @Schema(description = "Maximum transaction amount", example = "100.00")
    BigDecimal maxAmount
) {}
```

#### Key @Schema Attributes

| Attribute | Purpose | Example |
|-----------|---------|---------|
| `description` | Human-readable field description | `"Transaction amount in USD"` |
| `example` | Sample value for documentation | `"4.50"` |
| `requiredMode` | Whether field is required | `REQUIRED`, `NOT_REQUIRED` |
| `maxLength` | Maximum string length (match validation) | `255` |
| `defaultValue` | Default value if not provided | `"USD"` |
| `allowableValues` | Valid enum values | `{"INCOME", "EXPENSE"}` |

#### Enum Documentation

Annotate enums to provide descriptions for each value:

```java
@Schema(description = "Type of budget analysis error")
public enum BudgetAnalyzerError {
    @Schema(description = "The requested resource was not found")
    NOT_FOUND,

    @Schema(description = "The request data is invalid or malformed")
    INVALID_REQUEST,

    @Schema(description = "A business rule was violated")
    BUSINESS_RULE_VIOLATION,

    @Schema(description = "An internal service error occurred")
    INTERNAL_ERROR
}
```

#### Controller Annotations

**Required imports**:
```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
```

**Controller Pattern**:
```java
@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Transaction management operations")
public class TransactionController {

    @Operation(
        summary = "Get all transactions",
        description = "Retrieves all transactions with optional filtering and pagination"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Transactions retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = TransactionResponse.class))
            )
        )
    })
    @GetMapping
    public List<TransactionResponse> getAll(
        @Parameter(description = "Filter criteria", example = "coffee")
        @RequestParam(required = false) String search
    ) {
        // implementation
    }

    @Operation(
        summary = "Get transaction by ID",
        description = "Retrieves a single transaction by its unique identifier"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Transaction found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = TransactionResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Transaction not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(
                    name = "Not Found",
                    summary = "Transaction does not exist",
                    value = """
                        {
                          "type": "NOT_FOUND",
                          "title": "Resource Not Found",
                          "status": 404,
                          "detail": "Transaction not found with id: 12345"
                        }
                        """
                )
            )
        )
    })
    @GetMapping("/{id}")
    public TransactionResponse getById(
        @Parameter(description = "Transaction ID", example = "12345")
        @PathVariable Long id
    ) {
        // implementation
    }

    @Operation(
        summary = "Create transaction",
        description = "Creates a new transaction record"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Transaction created successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = TransactionResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        )
    })
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
        @Valid @RequestBody CreateTransactionRequest request
    ) {
        // implementation with Location header
    }
}
```

#### Coordination with Validation

**Rule**: SpringDoc annotations must coordinate with Bean Validation annotations:

```java
// ✅ CORRECT - Annotations are coordinated
@Schema(
    description = "Transaction description",
    maxLength = 255,                           // Matches @Size
    requiredMode = Schema.RequiredMode.REQUIRED // Matches @NotBlank
)
@NotBlank
@Size(max = 255)
String description

// ❌ WRONG - Annotations are inconsistent
@Schema(maxLength = 100)  // Says 100
@Size(max = 255)          // But validation allows 255
String description
```

#### Discovery

```bash
# Find all @Schema annotations in a service
grep -r "@Schema" src/main/java/*/api/

# Find controller OpenAPI annotations
grep -r "@Operation\|@ApiResponse" src/main/java/*/api/

# View complete examples
cat src/main/java/org/budgetanalyzer/transaction/api/request/TransactionUpdateRequest.java
cat src/main/java/org/budgetanalyzer/transaction/api/TransactionController.java
```

### Entities
- **Pattern**: Entity name (no suffix)
- **Package**: `*.domain`
- **Annotation**: `@Entity`
- **Example**: `Transaction`, `Budget`, `Category`

## Service Layer Boundary Rules

**CRITICAL**: Services must NOT import from the API package (`*.api.request` or `*.api.response`).

### Why This Matters
- **Layer isolation**: Service layer should be independent of HTTP/API concerns
- **Testability**: Services can be tested without API layer dependencies
- **Reusability**: Services can be called from multiple interfaces (API, messaging, scheduled tasks)
- **Clear responsibilities**: Controllers handle HTTP concerns; services handle business logic

### Request DTO Transformation

Controllers must transform API request DTOs before calling services:

```java
// ✅ CORRECT - Controller transforms request to primitives/entity
@PostMapping
public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody RoleRequest request) {
    var role = roleService.createRole(
        request.getName(),
        request.getDescription(),
        request.getParentRoleId()
    );
    return ResponseEntity.created(location).body(RoleResponse.from(role));
}

// ❌ WRONG - Service accepts API request DTO
@Service
public class RoleService {
    public Role createRole(RoleRequest request) {  // Service imports from api package!
        // ...
    }
}

// ✅ CORRECT - Service accepts primitives or service-layer DTOs
@Service
public class RoleService {
    public Role createRole(String name, String description, String parentRoleId) {
        var role = new Role();
        role.setName(name);
        role.setDescription(description);
        role.setParentRoleId(parentRoleId);
        return roleRepository.save(role);
    }
}
```

### Alternative: Service-Layer DTOs

For complex inputs with many parameters, create DTOs in `service/dto/`:

```java
// In service/dto/
public record CreateRoleCommand(String name, String description, String parentRoleId) {}

// In controller
@PostMapping
public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody RoleRequest request) {
    var command = new CreateRoleCommand(
        request.getName(),
        request.getDescription(),
        request.getParentRoleId()
    );
    var role = roleService.createRole(command);
    return ResponseEntity.created(location).body(RoleResponse.from(role));
}

// In service - imports only from service/dto package
public Role createRole(CreateRoleCommand command) {
    var role = new Role();
    role.setName(command.name());
    // ...
}
```

### Forbidden Patterns

```java
// ❌ Service importing from api.request
import org.budgetanalyzer.permission.api.request.RoleRequest;

// ❌ Service importing from api.response
import org.budgetanalyzer.permission.api.response.RoleResponse;

// ❌ Service method signature using API DTOs
public Role createRole(RoleRequest request) { }
public RoleResponse getRole(String id) { }
```

## Base Entity Classes

**Module**: service-core

**From the service-core module** (in `org.budgetanalyzer.core.domain`):

### AuditableEntity
Provides automatic audit tracking:
- `createdAt` - Timestamp when entity was created (immutable)
- `updatedAt` - Timestamp when entity was last modified (auto-updated)
- `createdBy` - User ID who created the entity (immutable, from Spring Security context)
- `updatedBy` - User ID who last modified the entity (from Spring Security context)

**How it works**:
- Timestamps are managed by JPA lifecycle callbacks (`@PrePersist`, `@PreUpdate`)
- User tracking is managed by Spring Data JPA auditing (`@CreatedBy`, `@LastModifiedBy`)
- `SecurityContextAuditorAware` bean extracts user ID from `Authentication.getName()`
- User fields are null when no authentication context exists (system operations, migrations)

**Location**: `service-core/src/main/java/org/budgetanalyzer/core/domain/AuditableEntity.java`

**Usage**:
```java
@Entity
public class Transaction extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // ... other fields
}

// Accessing audit fields
Transaction tx = repository.findById(id).orElseThrow();
Instant created = tx.getCreatedAt();       // When created
Instant updated = tx.getUpdatedAt();       // When last modified
String creator = tx.getCreatedBy();        // Who created it (e.g., "auth0|123...")
String modifier = tx.getUpdatedBy();       // Who last modified it
```

**Note**: User tracking requires Spring Security on the classpath. Services not using security will have null `createdBy`/`updatedBy` values.

### SoftDeletableEntity
Extends `AuditableEntity` with soft-delete support:
- `deleted` boolean field (default: false)
- `SoftDeleteListener` intercepts deletions
- Sets `deleted=true` instead of removing from database

**Location**: `service-core/src/main/java/org/budgetanalyzer/core/domain/SoftDeletableEntity.java`

**Usage**:
```java
@Entity
public class Transaction extends SoftDeletableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // ... other fields
}

// In repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>,
                                               SoftDeleteOperations<Transaction, Long> {
    // Automatically gets findByIdActive(), findAllActive()
}
```

**Note**: `SoftDeleteOperations` interface is also in service-core (`org.budgetanalyzer.core.repository`).

## Persistence Layer: Pure JPA

**CRITICAL RULE**: Use pure JPA (Jakarta Persistence API) exclusively. NO Hibernate-specific features.

**Forbidden**:
```java
❌ import org.hibernate.*;
❌ import org.hibernate.annotations.*;
❌ import org.hibernate.criterion.*;
```

**Allowed**:
```java
✅ import jakarta.persistence.*;
```

**Why?**
- **Portability**: Allows switching JPA providers without code changes
- **Standard compliance**: JPA is a specification with multiple implementations
- **Architectural discipline**: Maintains flexibility at minimal cost

## Exception Handling

**Module**: service-web

**Pattern**: Centralized exception handling with `@ControllerAdvice`

### Exception Hierarchy (from service-web)

**Location**: `service-web/src/main/java/org/budgetanalyzer/service/exception/`

**Base Exceptions**:
- `ServiceException` - Base for all service exceptions (500)
- `ClientException` - Base for client errors (400)

**Specific Exceptions**:
- `ResourceNotFoundException` - Entity not found (404)
- `InvalidRequestException` - Bad request data (400)
- `BusinessException` - Business rule violation (422)
- `ServiceUnavailableException` - Service unavailable (503)

### Usage in Controllers
```java
@GetMapping("/{id}")
public TransactionResponse getById(@PathVariable Long id) {
    var transaction = repository.findByIdActive(id)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
    return mapper.toResponse(transaction);
}
```

### Usage in Services
```java
@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction createTransaction(Transaction transaction) {
        if (transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Transaction amount must be positive");
        }
        return transactionRepository.save(transaction);
    }
}
```

### Global Exception Handler
Service-common provides `ServletApiExceptionHandler` that automatically converts exceptions to standardized API error responses. Just include the library - autoconfiguration will auto-discover it.

## Dependency Injection

**Pattern**: Constructor injection (preferred)

```java
@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    // Constructor injection - no @Autowired needed in modern Spring
    public TransactionService(TransactionRepository transactionRepository,
                              AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }
}
```

**Avoid**:
- Field injection (`@Autowired` on fields)
- Setter injection (except for optional dependencies)

## Configuration Classes

**Pattern**: `@Configuration` classes for Spring beans

```java
@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource() {
        // configuration
    }
}
```

**File naming**: `*Config.java`
**Package**: `*.config`

## API Versioning

**Pattern**: URL-based versioning

```java
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    // endpoints
}
```

**Rationale**: Simple, explicit, compatible with API gateway routing

## HTTP Response Patterns

### 201 Created with Location Header

**Pattern**: POST endpoints that create resources MUST return `201 Created` with a `Location` header pointing to the newly created resource.

**HTTP Standard**: RFC 7231 - The Location header provides the URI of the created resource. This is expected behavior in RESTful APIs and followed by all major APIs (Google, GitHub, Stripe, PayPal).

**Implementation**:
```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ResponseEntity<ResourceResponse> create(@Valid @RequestBody CreateRequest request) {
    var created = service.create(request.toEntity());

    var location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(created.getId())
        .toUri();

    return ResponseEntity.created(location).body(ResourceResponse.from(created));
}
```

**Key points**:
- Use `ResponseEntity.created(location)` to set both status and header
- Build Location URI with `ServletUriComponentsBuilder.fromCurrentRequest()`
- Always include response body (the created resource representation)
- Location must be an absolute URI (ServletUriComponentsBuilder handles this)

**Discovery**:
```bash
# Find all POST endpoints in a service
grep -r "@PostMapping" src/main/java/*/api/

# Find examples using ResponseEntity.created
grep -rA 10 "ResponseEntity.created" src/main/java/
```

**Example**: See `AdminCurrencySeriesController.create()` in currency-service for a complete implementation.

## Testing Patterns

See [testing-patterns.md](testing-patterns.md) for detailed testing guidelines.

**Quick reference**:
- Unit tests: `*Test.java` (no Spring context)
- Integration tests: `*IntegrationTest.java` (with `@SpringBootTest`)
- Use TestContainers for database/Redis/RabbitMQ
- Minimum 80% code coverage

## Documentation Standards

**Quick reference**:
- All public APIs need Javadoc
- First sentence of Javadoc must end with period
- **REST endpoints**: See [OpenAPI Documentation with SpringDoc](#openapi-documentation-with-springdoc) section above
- All request/response DTOs must have `@Schema` annotations
- All controller endpoints must have `@Operation` and `@ApiResponse` annotations
- Domain models documented in architecture documentation

---

## External Links (GitHub Web Viewing)

*The relative paths in this document are optimized for Claude Code. When viewing on GitHub, use this link to access the referenced controller:*

- [AdminCurrencySeriesController.java](https://github.com/budgetanalyzer/currency-service/blob/main/src/main/java/org/budgetanalyzer/currency/api/AdminCurrencySeriesController.java)
