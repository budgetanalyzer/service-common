# Common Design Patterns - Budget Analyzer Microservices

## Production-Quality Code Principle

**RULE**: All code must be production-ready. No shortcuts, prototypes, or workarounds.

### Requirements
- Follow established design patterns
- Implement proper error handling and validation
- Write comprehensive tests
- Ensure thread safety where applicable
- Use appropriate logging levels
- Handle edge cases explicitly

### Why This Matters
- **Maintainability**: Future developers can understand and modify code
- **Reliability**: Production systems require robust, tested code
- **Professionalism**: Code quality reflects project standards
- **Technical Debt**: Shortcuts today create problems tomorrow

## SOLID Principles

### Single Responsibility Principle (SRP)
**Rule**: A class should have only one reason to change

```java
// ❌ WRONG - Multiple responsibilities
public class UserService {
    public void saveUser(User user) { }
    public void sendEmail(User user) { }  // Email logic doesn't belong here
    public void generateReport(User user) { }  // Reporting doesn't belong here
}

// ✅ CORRECT - Single responsibility
public class UserService {
    public void saveUser(User user) { }
}

public class EmailService {
    public void sendEmail(User user) { }
}

public class ReportService {
    public void generateReport(User user) { }
}
```

### Open/Closed Principle (OCP)
**Rule**: Classes should be open for extension but closed for modification

```java
// ✅ GOOD - Use interfaces for extensibility
public interface ExchangeRateProvider {
    Map<LocalDate, BigDecimal> getExchangeRates(CurrencySeries series, LocalDate startDate);
}

// New providers can be added without modifying existing code
public class FredExchangeRateProvider implements ExchangeRateProvider { }
public class EcbExchangeRateProvider implements ExchangeRateProvider { }
```

### Liskov Substitution Principle (LSP)
**Rule**: Subtypes must be substitutable for their base types

```java
// Parent behavior should work for all children
public class AuditableEntity {
    public void setUpdatedAt(Instant timestamp) { }
}

// Child class maintains parent contract
public class SoftDeletableEntity extends AuditableEntity {
    // Still honors setUpdatedAt contract
}
```

### Interface Segregation Principle (ISP)
**Rule**: Clients should not depend on interfaces they don't use

```java
// ❌ WRONG - Fat interface
public interface Repository {
    void save();
    void delete();
    void softDelete();  // Not all repositories need this
    void restore();     // Not all repositories need this
}

// ✅ CORRECT - Segregated interfaces
public interface Repository {
    void save();
    void delete();
}

public interface SoftDeleteOperations {
    void softDelete();
    void restore();
}
```

### Dependency Inversion Principle (DIP)
**Rule**: Depend on abstractions, not concretions

```java
// ❌ WRONG - Depends on concrete implementation
public class CurrencyService {
    private FredClient fredClient;  // Direct dependency
}

// ✅ CORRECT - Depends on abstraction
public class CurrencyService {
    private ExchangeRateProvider provider;  // Interface dependency
}
```

## General Design Patterns

### 1. Favor Composition Over Inheritance

```java
// ❌ AVOID - Deep inheritance hierarchies
public class Transaction extends BaseEntity extends AuditableEntity extends ... { }

// ✅ PREFER - Composition
public class Transaction extends AuditableEntity {
    private TransactionMetadata metadata;
    private TransactionValidation validation;
}
```

### 2. Program to Interfaces, Not Implementations

```java
// ❌ WRONG
private ArrayList<Transaction> transactions;
private FredExchangeRateProvider provider;

// ✅ CORRECT
private List<Transaction> transactions;
private ExchangeRateProvider provider;
```

### 3. Use Dependency Injection

**Always use constructor injection** (preferred over field injection):

```java
// ✅ CORRECT - Constructor injection
@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    // No @Autowired needed in modern Spring
    public TransactionService(TransactionRepository transactionRepository,
                              AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }
}

// ❌ AVOID - Field injection
@Service
public class TransactionService {
    @Autowired  // Hard to test, hides dependencies
    private TransactionRepository transactionRepository;
}
```

**Why constructor injection?**
- Immutability: Dependencies are final
- Testability: Easy to mock in tests
- Explicit dependencies: All requirements visible in constructor
- Null safety: Spring ensures dependencies are injected before use

### 4. Avoid Static Methods

**Exception**: Pure utility functions with no state

```java
// ✅ ACCEPTABLE - Pure utility function
public class StringUtils {
    public static String capitalize(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}

// ❌ WRONG - Static method with side effects
public class TransactionService {
    public static void saveTransaction(Transaction t) {
        // Static methods can't be mocked, break testing
    }
}
```

### 5. Immutability

**Use final fields where possible**:

```java
// ✅ GOOD - Immutable fields
public class TransactionService {
    private final TransactionRepository repository;  // Can't be reassigned
    private final AuditService auditService;

    public TransactionService(TransactionRepository repository,
                             AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }
}
```

### 6. Null Safety

**Use Optional for potentially null returns**:

```java
// ❌ AVOID - Nullable return
public Transaction findById(Long id) {
    return repository.findById(id);  // Could be null!
}

// ✅ PREFER - Optional return
public Optional<Transaction> findById(Long id) {
    return repository.findById(id);
}

// ✅ ALSO GOOD - Throw exception if not found
public Transaction getById(Long id) {
    return repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
}
```

## Spring Boot Specific Patterns

### 1. Use Constructor Injection

See "Use Dependency Injection" section above.

### 2. Avoid @Autowired on Fields

```java
// ❌ WRONG
@Service
public class TransactionService {
    @Autowired
    private TransactionRepository repository;
}

// ✅ CORRECT
@Service
public class TransactionService {
    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }
}
```

### 3. Keep Controllers Thin

**Controllers handle HTTP concerns only - delegate everything to services**:

```java
// ✅ CORRECT - Thin controller
@RestController
@RequestMapping("/v1/transactions")
public class TransactionController {
    private final TransactionService service;

    @GetMapping("/{id}")
    public TransactionResponse getById(@PathVariable Long id) {
        var transaction = service.getById(id);  // Service retrieves entity
        return TransactionResponse.from(transaction);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteTransaction(id);  // Service handles deletion
        return ResponseEntity.noContent().build();
    }
}
```

### 4. Use @Transactional Only at Service Layer

```java
// ✅ CORRECT - Transaction at service layer
@Service
public class TransactionService {
    @Transactional
    public Transaction create(Transaction transaction) {
        validate(transaction);
        return repository.save(transaction);
    }
}

// ❌ WRONG - Transaction at controller layer
@RestController
public class TransactionController {
    @Transactional  // HTTP layer shouldn't manage transactions
    @PostMapping
    public TransactionResponse create(@RequestBody TransactionRequest request) { }
}
```

### 5. Leverage Spring Boot Starters

**Use starters for consistent configuration**:
- `spring-boot-starter-web` - Web applications
- `spring-boot-starter-data-jpa` - JPA repositories
- `spring-boot-starter-validation` - Bean Validation
- `spring-boot-starter-actuator` - Metrics and health checks
- `spring-boot-starter-test` - Testing dependencies

### 6. Use @ConfigurationProperties for Type-Safe Configuration

```java
// ✅ CORRECT - Type-safe configuration
@ConfigurationProperties(prefix = "budget-analyzer")
public record BudgetAnalyzerProperties(
    @Valid Map<String, CsvConfig> csvConfigMap
) {}

// Usage in service
@Service
public class CsvImportService {
    private final BudgetAnalyzerProperties properties;

    public void importCsv(String format) {
        var config = properties.csvConfigMap().get(format);
        // ...
    }
}
```

### 7. Implement Health Checks via Actuator

The `prometheus` endpoint is automatically exposed by service-core. Add other endpoints as needed:

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics  # prometheus is added automatically
  health:
    readiness:
      enabled: true
    liveness:
      enabled: true
```

## Database Patterns

### 1. Use JPA Specifications for Dynamic Queries

```java
// ✅ GOOD - Type-safe dynamic queries
public class TransactionSpecifications {
    public static Specification<Transaction> hasAmount(BigDecimal amount) {
        return (root, query, cb) -> cb.equal(root.get("amount"), amount);
    }

    public static Specification<Transaction> dateAfter(LocalDate date) {
        return (root, query, cb) -> cb.greaterThan(root.get("date"), date);
    }
}

// Usage
var spec = Specification.where(hasAmount(amount))
                       .and(dateAfter(startDate));
repository.findAll(spec);
```

### 2. Avoid N+1 Queries

```java
// ❌ WRONG - N+1 query
@Entity
public class Transaction {
    @ManyToOne
    private Category category;  // Lazy loaded - causes N+1
}

List<Transaction> transactions = repository.findAll();
for (Transaction t : transactions) {
    System.out.println(t.getCategory().getName());  // N queries!
}

// ✅ CORRECT - JOIN FETCH
@Query("SELECT t FROM Transaction t JOIN FETCH t.category")
List<Transaction> findAllWithCategory();
```

### 3. Index Foreign Keys and Frequently Queried Columns

```sql
-- Always index foreign keys
CREATE INDEX idx_transaction_category_id ON transaction(category_id);

-- Index frequently searched columns
CREATE INDEX idx_transaction_date ON transaction(date);
CREATE INDEX idx_transaction_currency ON transaction(currency_iso_code);
```

### 4. Use Optimistic Locking Where Appropriate

```java
@Entity
public class Account {
    @Version
    private Long version;  // Optimistic locking for concurrent updates
}
```

### 5. Never Expose Entities Directly in API Responses

```java
// ❌ WRONG - Exposing entity
@GetMapping("/{id}")
public Transaction getById(@PathVariable Long id) {
    return repository.findById(id).orElseThrow();  // Exposes internal structure
}

// ✅ CORRECT - Use DTO
@GetMapping("/{id}")
public TransactionResponse getById(@PathVariable Long id) {
    var transaction = service.getById(id);
    return TransactionResponse.from(transaction);  // Maps to DTO
}
```

### 6. Use Projections for Read-Heavy Operations

```java
// For large entities, use projections to fetch only needed fields
public interface TransactionSummary {
    Long getId();
    LocalDate getDate();
    BigDecimal getAmount();
}

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<TransactionSummary> findAllProjectedBy();  // Only fetches 3 fields
}
```

## Security Best Practices

### 1. Validate All Inputs at Controller Layer

```java
@PostMapping
public TransactionResponse create(@Valid @RequestBody TransactionRequest request) {
    // @Valid triggers Bean Validation
}

public record TransactionRequest(
    @NotBlank String description,
    @NotNull @Positive BigDecimal amount
) {}
```

### 2. Safe Logging with SafeLogger

`SafeLogger` is an **opt-in** utility — it does NOT wrap SLF4J and does NOT intercept log calls
automatically. You must explicitly call its methods when logging sensitive data.

**API reference:**

```java
// Serialize an object to JSON with @Sensitive fields masked
log.info("User created: {}", SafeLogger.toJson(user));

// Mask a sensitive string (completely or showing last N chars)
log.info("Token: {}", SafeLogger.mask(token));           // "********"
log.info("Card: {}", SafeLogger.mask(cardNumber, 4));     // "************3456"

// Truncate an identifier for safe logging (prefix only)
log.info("Session: {}", SafeLogger.truncateId(sessionId));       // "550e8400…"
log.info("State: {}", SafeLogger.truncateId(state, 6));          // "a1b2c3…"
// null → "[null]", empty or too short → "***"
```

**What IS automatic** (when HTTP logging is enabled via `budgetanalyzer.service.http-logging`):
- **Header masking** — `Authorization`, `Cookie`, `Set-Cookie`, and other sensitive headers are
  redacted automatically by the HTTP logging filters
- **Body field sanitization** — JSON and form body fields matching sensitive names (`password`,
  `secret`, `token`, `apikey`, etc.) are redacted automatically
- **Query parameter sanitization** — OAuth2 and credential-related query params (`code`, `state`,
  `token`, `password`, `secret`, etc.) are redacted automatically

**What is NOT automatic** — you must handle explicitly:
- Identifiers logged in application code (session IDs, user IDs, OAuth2 state values)
- Objects logged via SLF4J `{}` placeholders — use `SafeLogger.toJson(obj)` for `@Sensitive` masking
- Any sensitive string passed directly to a log statement

### 3. Never Log Sensitive Information

```java
// ❌ WRONG
log.info("Processing payment for card: {}", creditCardNumber);
log.info("Session started: {}", sessionId);

// ✅ CORRECT
log.info("Processing payment for card ending in: {}", SafeLogger.mask(creditCardNumber, 4));
log.info("Session started: {}", SafeLogger.truncateId(sessionId));
```

### 4. Use HTTPS in Production

Configure in `application.yml` for production:
```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
```

## Performance Best Practices

### 1. Use Pagination for List Endpoints

```java
@GetMapping("/search")
public PagedResponse<TransactionResponse> search(
    TransactionFilter filter,
    Pageable pageable
) {
    var page = transactionService.search(filter, pageable);
    return PagedResponse.from(page, TransactionResponse::from);
}
```

Use Spring-native request binding with `Pageable` and return `PagedResponse<T>` as the public API
contract. Avoid exposing raw `Page` or `PageImpl` JSON directly.

### 2. Implement Caching Where Appropriate

```java
@Cacheable("exchangeRates")
public List<ExchangeRate> getExchangeRates(String currency, LocalDate startDate, LocalDate endDate) {
    return repository.findByCurrencyAndDateBetween(currency, startDate, endDate);
}

@CacheEvict(value = "exchangeRates", allEntries = true)
public void importLatestRates() {
    // Import logic - cache cleared after
}
```

### 3. Use Async Processing for Long-Running Operations

```java
@Async
public CompletableFuture<ImportResult> importLargeFile(MultipartFile file) {
    // Process in background
    return CompletableFuture.completedFuture(result);
}
```

### 4. Use Connection Pooling

Spring Boot uses HikariCP by default (optimal configuration):
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
```

## Resources

- [Spring Boot Best Practices](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Effective Java by Joshua Bloch](https://www.oreilly.com/library/view/effective-java/9780134686097/)
- [Clean Code by Robert C. Martin](https://www.oreilly.com/library/view/clean-code-a/9780136083238/)
- [SOLID Principles](https://en.wikipedia.org/wiki/SOLID)
