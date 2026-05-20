# Testing Patterns - Budget Analyzer Microservices

## Testing Philosophy

**CRITICAL PRINCIPLE**: Tests must always be written for how components **should** behave according to their specification and real-world requirements, NOT around defective implementations.

### When Tests Fail Due to Implementation Issues

1. **STOP immediately** - Do not attempt to work around the implementation deficiency
2. **Analyze the failure** - Determine if the test is correct or if the implementation is deficient
3. **Explain the situation** to the team
4. **Fix the implementation** or update requirements - never write tests around bugs

### NEVER Disable Tests

**CRITICAL**: The `@Disabled` annotation must NEVER be used to skip broken or failing tests.

**If a test is broken and cannot be fixed**:
1. **STOP immediately** - Do not proceed with other work
2. **Notify the user** - Explain what's broken and why it can't be fixed
3. **Wait for guidance** - The user must decide how to proceed

**Why `@Disabled` is forbidden**:
- Disabled tests are invisible failures that accumulate technical debt
- They mask real problems that need to be addressed
- They create a false sense of test coverage
- They are rarely revisited and become permanent gaps in testing

```java
// ❌ NEVER DO THIS
@Disabled("Broken - needs to be fixed later")
@Test
void shouldProcessTransaction() {
    // This test will be forgotten and never fixed
}

// ❌ NEVER DO THIS
@Disabled("Waiting for feature X")
@Test
void shouldHandleNewFeature() {
    // Use proper feature flags or don't write the test yet
}
```

### NEVER Modify Unrelated Tests

**CRITICAL**: Do not change, weaken, or delete existing tests you did not write to make new or changed tests pass.

- **Do not modify shared test infrastructure** (base classes, fixtures, configuration) to fix failures in your new code
- **If existing tests break after your changes**, your code is wrong — fix the implementation, not the tests
- **If unrelated tests were already failing** before your changes, STOP and surface the issue to the user
- **`./gradlew clean build` is a real verification step** — passing it by weakening the test suite is not passing it

```java
// ❌ NEVER DO THIS — gutting an existing test to make yours pass
@Test
void shouldValidateAmount() {
    // Changed from assertThatThrownBy to just checking non-null
    // because my new validation broke the old behavior
    assertThat(service.validate(amount)).isNotNull();
}

// ❌ NEVER DO THIS — modifying shared test config for your feature
// Changed TestSecurityConfig to disable auth so my tests don't need tokens
@TestConfiguration
public class TestSecurityConfig {
    // gutted to make new tests pass
}
```

**What to do instead**:
- **Your new tests fail?** → Fix your implementation to match existing behavior
- **Existing tests fail after your changes?** → Your changes broke something — revert or fix your code
- **Shared infrastructure incompatible with your needs?** → STOP and ask the user how to proceed
- **Tests were already failing?** → STOP and notify the user before proceeding

**Acceptable (rare) uses of `@Disabled`**:
- Temporarily during active development within a single PR (must be removed before merge)
- Platform-specific tests with `@DisabledOnOs` or `@EnabledOnOs` (not broken, just not applicable)

**What to do instead**:
- **Broken test?** → Fix it or delete it
- **Can't fix it?** → STOP and notify the user
- **Test for unimplemented feature?** → Don't write the test until the feature exists
- **Flaky test?** → Fix the flakiness, don't disable it

### Core Principle: Test Behavior, Not Implementation

This codebase uses a layered testing strategy that prioritizes real integration over mocked abstractions. Tests should give us confidence to refactor and upgrade dependencies independently.

### Testing Layers

#### 1. API Contract Tests (Primary Confidence Layer)
- Black-box tests against the actual HTTP API
- Run in CI/CD against deployed services
- Test the contract, not the framework
- **Benefit**: Swap Spring Boot for anything else - if API tests pass, you're good

#### 2. Infrastructure Integration Tests (Testcontainers)
- Test each infrastructure boundary: database, messaging, caching, external APIs
- Use real dependencies via testcontainers (Postgres, RabbitMQ, Redis, etc.)
- Verify YOUR configuration works, not that Spring works
- **Example**: Test that your retry logic + DLQ routing works, not that RabbitMQ has a DLQ

#### 3. Unit Tests (Business Logic Only)
- Pure functions and domain logic
- No framework dependencies
- If it's simple CRUD with no business rules, skip the test

### What We Don't Test

#### No Mocks of Internal Components
**Hard rule**: No `@MockBean`, no Mockito on repositories/services/controllers.

**Why**: Mocks drift from reality, create false confidence, and couple tests to implementation details. When you upgrade Spring Boot, you shouldn't have to rewrite test infrastructure.

**Exception**: External system boundaries (third-party APIs via WireMock, time via `Clock` injection). Mock things you don't control, not things you wrote.

#### No Framework Verification Tests
Don't test that Spring does what Spring says it does:
- Don't test that `@Scheduled` annotations work
- Don't test that `@Transactional` creates transactions
- Don't test that validation annotations validate

**Test YOUR code**: Does your scheduler configuration use the right cron? Does your transaction boundary make sense for your use case?

### Development Workflow

1. Write API test for the contract
2. Implement feature
3. Add testcontainers integration test if touching infrastructure
4. Add unit test if complex business logic exists
5. Run tests - they should fail for the right reasons (your bug, not Spring's)

### Upgrade Strategy

When upgrading Spring Boot:
- API tests remain unchanged (framework agnostic)
- Integration tests may need configuration updates
- Unit tests remain unchanged (no framework dependencies)

This separation lets you validate the upgrade without rewriting the entire test suite.

> Simple code doesn't need tests proving it's simple. Complex infrastructure needs real integration tests, not mocked fantasies. The API contract is the only promise that matters.

## Test Types

### Unit Tests

**File Pattern**: `*Test.java`
**Location**:
- `service-core/src/test/java` (for service-core tests)
- `service-web/src/test/java` (for service-web tests)
- `{service-name}/src/test/java` (for individual service tests)

**Framework**: JUnit 5 (Jupiter) + AssertJ
**Spring Context**: No

**Characteristics**:
- Fast execution (milliseconds)
- No external dependencies
- No Spring context loading
- Uses mocks for dependencies

**Example**:
```java
class TransactionServiceTest {

    @Test
    void shouldCalculateTotalAmount() {
        var service = new TransactionService();
        var result = service.calculateTotal(transactions);
        assertThat(result).isEqualByComparingTo(new BigDecimal("150.00"));
    }
}
```

### Integration Tests

**File Pattern**: `*IntegrationTest.java`
**Location**:
- `service-core/src/test/java` (for service-core integration tests)
- `service-web/src/test/java` (for service-web integration tests)
- `{service-name}/src/test/java` (for individual service integration tests)

**Framework**: JUnit 5 + Spring Boot Test + AssertJ
**Spring Context**: Yes

**Characteristics**:
- Slower execution (seconds)
- Tests multiple components together
- Loads Spring context
- Uses TestContainers for real database/Redis/RabbitMQ

**Example**:
```java
@SpringBootTest
@Testcontainers
class TransactionRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private TransactionRepository repository;

    @Test
    void shouldSaveAndRetrieveTransaction() {
        var transaction = new Transaction();
        transaction.setAmount(new BigDecimal("100.00"));

        var saved = repository.save(transaction);
        var retrieved = repository.findByIdActive(saved.getId());

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getId()).isEqualTo(saved.getId());
    }
}
```

### Controller Tests

**File Pattern**: `*ControllerTest.java`
**Location**: `src/test/java` (mirror of `src/main/java`)
**Framework**: JUnit 5 + MockMvc + AssertJ
**Spring Context**: Partial (Web Layer only)

**Characteristics**:
- Tests HTTP layer only
- Uses `@WebMvcTest` for faster execution
- Mocks service dependencies
- Validates request/response format

**Example**:
```java
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Test
    void shouldReturnTransactionById() throws Exception {
        var transaction = new Transaction();
        transaction.setId(1L);
        transaction.setAmount(new BigDecimal("100.00"));

        when(transactionService.findById(1L)).thenReturn(Optional.of(transaction));

        mockMvc.perform(get("/api/v1/transactions/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.amount").value(100.00));
    }

    @Test
    void shouldReturn404WhenTransactionNotFound() throws Exception {
        when(transactionService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/transactions/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn201WithLocationHeaderWhenCreated() throws Exception {
        var transaction = new Transaction();
        transaction.setId(42L);
        transaction.setAmount(new BigDecimal("100.00"));

        when(transactionService.create(any())).thenReturn(transaction);

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 100.00}"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(header().string("Location", containsString("/api/v1/transactions/42")))
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.amount").value(100.00));
    }
}
```

**Note**: For testing 201 Created responses, always verify both the Location header and the response body. See [spring-boot-conventions.md](spring-boot-conventions.md#201-created-with-location-header) for the controller implementation pattern.

## TestContainers

**Purpose**: Provide real infrastructure (database, Redis, RabbitMQ) for integration tests

### PostgreSQL Container

```java
@Testcontainers
class DatabaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("test_db")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

### Redis Container

```java
@Container
static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
    .withExposedPorts(6379);

@DynamicPropertySource
static void configureRedis(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", redis::getFirstMappedPort);
}
```

## Mocking Strategies

### Use Mockito for Dependencies

```java
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void shouldCreateTransaction() {
        var transaction = new Transaction();
        when(transactionRepository.save(any())).thenReturn(transaction);

        var result = transactionService.create(transaction);

        verify(transactionRepository).save(transaction);
        verify(auditService).logCreation(transaction);
    }
}
```

### ArgumentCaptor for Complex Verification

```java
@Test
void shouldSaveTransactionWithCorrectTimestamp() {
    ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

    transactionService.create(transaction);

    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getCreatedAt()).isNotNull();
}
```

## Test Coverage Goals

### Minimum Coverage
- **Overall**: 80% code coverage
- **Critical paths**: 100% coverage
- **Utilities**: 100% coverage
- **Controllers**: 90% coverage
- **Services**: 85% coverage

### Coverage Tools
JaCoCo report generation and coverage verification are enabled for Java service
modules. `check` enforces the active gates after running tests.

```bash
# Run tests with coverage (all modules)
./gradlew test jacocoTestReport

# View coverage reports
# service-core report:
open service-core/build/reports/jacoco/test/html/index.html

# service-web report:
open service-web/build/reports/jacoco/test/html/index.html
```

Active service-common gates:

| Module | Line gate | Branch gate | Current baseline |
|---|---:|---:|---|
| `service-core` | 80% | 70% | 81.52% line / 72.27% branch |
| `service-web` | 93% | 80% | 94.20% line / 81.27% branch |

`service-core` has a temporary lower branch gate because its Phase 1 baseline is
below the shared-library branch target. Raise it to at least 75% once targeted
tests cover the remaining core utility conditionals.

## Testing Best Practices

### 1. Test Naming Convention

**CRITICAL**: Test method names MUST clearly express expected behavior and conditions using camelCase without underscores. Use `@DisplayName` only when the functionality is not obvious from the method name alone.

**Allowed patterns** (choose one and be consistent within a test class):
- `should[Behavior]When[Condition]` - BDD style (recommended)
- `should[Behavior]` - When condition is obvious from context
- `[behavior][Condition]` - Direct style (e.g., `throwsExceptionWhenAmountNegative`)
```java
// ✅ GOOD - Acceptable patterns
@Test
void shouldThrowExceptionWhenAmountIsNegative() { }

@Test
void shouldReturnEmptyListWhenNoTransactionsExist() { }

@Test
void throwsExceptionWhenAmountIsNegative() { }

@Test
void returnsEmptyListWhenNoTransactionsExist() { }

// ✅ GOOD - @DisplayName used only when needed for clarity
@Test
@DisplayName("should handle edge case where transaction date falls on leap day during DST transition")
void shouldHandleLeapDayDstEdgeCase() { }

// ❌ BAD - Underscores violate checkstyle
@Test
void when_amount_is_negative_then_throws_exception() { }

// ❌ BAD - Unclear intent
@Test
void testCalculate() { }

@Test
void negativeAmount() { }

// ❌ BAD - Unnecessary @DisplayName when method name is already clear
@Test
@DisplayName("should calculate total correctly")
void shouldCalculateTotalCorrectly() { }
```

**Key principle**: The method name must answer "What behavior is being verified and under what conditions?" in clear camelCase.
### 2. Arrange-Act-Assert Pattern

```java
@Test
void shouldCalculateTotal() {
    // Arrange - Set up test data
    var transactions = List.of(
        createTransaction(100.00),
        createTransaction(50.00)
    );

    // Act - Execute the method
    var total = service.calculateTotal(transactions);

    // Assert - Verify the result
    assertThat(total).isEqualByComparingTo(new BigDecimal("150.00"));
}
```

### 3. Test One Thing Per Test

```java
// ❌ BAD - Testing multiple scenarios
@Test
void testTransactionCreation() {
    // Tests both creation AND validation
}

// ✅ GOOD - Separate tests
@Test
void shouldCreateTransactionSuccessfully() { }

@Test
void shouldThrowExceptionWhenAmountIsInvalid() { }
```

### 4. Use Test Fixtures

```java
class TransactionTestFixtures {

    static Transaction createTransaction() {
        return createTransaction("100.00", "Test");
    }

    static Transaction createTransaction(String amount, String description) {
        var transaction = new Transaction();
        transaction.setAmount(new BigDecimal(amount));
        transaction.setDescription(description);
        transaction.setDate(LocalDate.now());
        return transaction;
    }
}
```

### 5. Use TestConstants for Test Data

**CRITICAL**: Always use named constants from a `TestConstants` class instead of string literals or magic numbers in test values. This improves maintainability, readability, and consistency across tests.

```java
// TestConstants.java
public final class TestConstants {
    // Prevent instantiation
    private TestConstants() {}

    // Currency codes
    public static final String CURRENCY_CODE_USD = "USD";
    public static final String CURRENCY_CODE_EUR = "EUR";
    public static final String CURRENCY_CODE_INVALID = "INVALID";

    // Test amounts
    public static final String AMOUNT_100 = "100.00";
    public static final String AMOUNT_NEGATIVE = "-50.00";
    public static final String AMOUNT_ZERO = "0.00";

    // Test descriptions
    public static final String DESCRIPTION_TEST = "Test transaction";
    public static final String DESCRIPTION_EMPTY = "";

    // Test IDs
    public static final Long ID_1 = 1L;
    public static final Long ID_NONEXISTENT = 999L;

    // Test dates
    public static final String DATE_2024_01_01 = "2024-01-01";
}
```

```java
// ❌ BAD - Magic strings and numbers
@Test
void shouldCreateTransaction() {
    var transaction = new Transaction();
    transaction.setAmount(new BigDecimal("100.00"));
    transaction.setDescription("Test");
    transaction.setCurrencyCode("USD");
}

@Test
void shouldRejectInvalidCurrencyCode() {
    mockMvc.perform(post("/api/v1/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currencyCode\": \"INVALID\"}"))
        .andExpect(status().isBadRequest());
}

// ✅ GOOD - Using TestConstants
@Test
void shouldCreateTransaction() {
    var transaction = new Transaction();
    transaction.setAmount(new BigDecimal(TestConstants.AMOUNT_100));
    transaction.setDescription(TestConstants.DESCRIPTION_TEST);
    transaction.setCurrencyCode(TestConstants.CURRENCY_CODE_USD);
}

@Test
void shouldRejectInvalidCurrencyCode() {
    mockMvc.perform(post("/api/v1/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currencyCode\": \"" + TestConstants.CURRENCY_CODE_INVALID + "\"}"))
        .andExpect(status().isBadRequest());
}
```

**Benefits**:
- **Consistency**: Same test value used across all tests
- **Maintainability**: Change value in one place
- **Readability**: `CURRENCY_CODE_USD` is clearer than `"USD"`
- **Discoverability**: IDE autocomplete shows all available constants
- **Type safety**: Constants have clear types and naming patterns

**Organization**:
- Group related constants together (currencies, amounts, dates, etc.)
- Use descriptive names that indicate the value's purpose
- Include edge case values (empty, null, invalid, boundary values)
- Place `TestConstants` in `src/test/java/org/budgetanalyzer/{service}/util/`

### 6. Test Edge Cases

```java
@Test
void shouldHandleNullValues() { }

@Test
void shouldHandleEmptyList() { }

@Test
void shouldHandleVeryLargeNumbers() { }

@Test
void shouldHandleSpecialCharacters() { }
```

### 7. Don't Test Framework Code - Test Business Logic

**CRITICAL PRINCIPLE**: Never write tests that verify framework behavior. If Spring, JPA, Modulith, ShedLock, RabbitMQ, or any other framework/library doesn't work correctly, that's THEIR bug, not ours. Testing frameworks pollutes the codebase with low-value tests that provide no real confidence in OUR code.

**What NOT to test**:
- Framework mechanisms (Spring dependency injection, JPA persistence, transaction management)
- Third-party library behavior (Modulith event publishing, ShedLock distributed locking)
- Infrastructure components doing what they're supposed to do (RabbitMQ message delivery, Redis caching)
- Configuration working as documented (application properties being loaded)

**What TO test**:
- Business logic and domain rules
- Data validation and transformations
- Error handling and edge cases specific to your domain
- Integration points where YOUR code interacts with frameworks (e.g., "does this service method trigger the correct event with the correct data?")

```java
// ❌ BAD - Testing JPA's save method
@Test
void shouldSaveToDatabase() {
    repository.save(transaction);
    var found = repository.findById(transaction.getId());
    assertThat(found).isPresent();
    // This just tests that JPA works - provides no value
}

// ❌ BAD - Testing that Modulith publishes events
@Test
void shouldPublishEventWhenTransactionCreated() {
    service.createTransaction(transaction);
    // Verify that ApplicationEventPublisher was called
    // This just tests that Modulith/Spring events work - not our concern
}

// ❌ BAD - Testing that ShedLock prevents concurrent execution
@Test
void shouldPreventConcurrentScheduledTaskExecution() {
    // Spawning multiple threads and verifying ShedLock prevents concurrent runs
    // This tests ShedLock's functionality - if it's broken, that's their bug
}

// ❌ BAD - Testing that @Transactional works
@Test
void shouldRollbackOnException() {
    assertThatThrownBy(() -> service.failingMethod())
        .isInstanceOf(Exception.class);
    assertThat(repository.count()).isZero();
    // This tests Spring's transaction management - not our business logic
}

// ❌ BAD - Testing that @Cacheable works
@Test
void shouldCacheResults() {
    service.getData(1L); // First call
    service.getData(1L); // Second call
    verify(repository, times(1)).findById(1L); // Verify cache hit
    // This tests Spring Cache abstraction - not our logic
}

// ✅ GOOD - Test business logic and validation
@Test
void shouldValidateAmountBeforeSaving() {
    var negativeAmount = new Transaction();
    negativeAmount.setAmount(new BigDecimal("-100.00"));

    assertThatThrownBy(() -> service.create(negativeAmount))
        .isInstanceOf(BusinessException.class);
    // Tests OUR validation rule - this is our domain logic
}

// ✅ GOOD - Test data transformation and business rules
@Test
void shouldCalculateTotalWithCorrectExchangeRates() {
    var transactions = List.of(
        createTransaction("100.00", "USD"),
        createTransaction("50.00", "EUR")
    );

    var total = service.calculateTotalInUSD(transactions);

    assertThat(total).isEqualByComparingTo(new BigDecimal("145.00"));
    // Tests OUR business logic for currency conversion
}

// ✅ GOOD - Test integration behavior (not framework mechanism)
@Test
void shouldIncludeCorrectDataInPublishedEvent() {
    var transaction = service.createTransaction(createRequest());

    // Capture the event that was published
    var event = eventCaptor.getValue();
    assertThat(event.getTransactionId()).isEqualTo(transaction.getId());
    assertThat(event.getAmount()).isEqualTo(transaction.getAmount());
    // Tests that OUR code publishes the right data, not that Modulith works
}
```

**Key Questions to Ask**:
1. "Am I testing MY code or the framework's code?"
2. "If this test fails, is it because of a bug in my business logic, or because a third-party library is broken?"
3. "Would this test catch a real bug in my domain logic, or just verify that Spring/JPA/Modulith does what it's supposed to?"

**Remember**: Framework tests are expensive to maintain and provide false confidence. Focus test effort on business logic where bugs actually occur.

## Testing Soft-Delete Entities

```java
@Test
void shouldSoftDeleteTransaction() {
    var transaction = repository.save(new Transaction());

    repository.delete(transaction);

    // Should not be in active records
    assertThat(repository.findByIdActive(transaction.getId())).isEmpty();

    // But should still exist in database
    assertThat(repository.findById(transaction.getId())).isPresent();
    assertThat(repository.findById(transaction.getId()).get().isDeleted()).isTrue();
}
```

## Testing Exception Handling

### API Error Response Contract

**CRITICAL**: When testing API error responses, only assert on the **stable contract** fields. Message fields are subject to change and should NOT be used in assertions.

**Stable contract fields** (safe to assert on):
- HTTP status code
- Error type (`$.type`)
- Error code (`$.code`)
- Field errors (`$.fieldErrors[].field`, `$.fieldErrors[].code`)

**Unstable fields** (DO NOT assert on):
- Message text (`$.message`, `$.fieldErrors[].message`)
- Any descriptive text fields

These contracts are specified in the OpenAPI specification and form the programmatic API that clients depend on.

### Examples

```java
// ✅ GOOD - Assert on stable contract fields
@Test
void shouldReturn422WhenCurrencyCodeAlreadyExists() throws Exception {
    mockMvc.perform(post("/v1/admin/currencies")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(containsString("APPLICATION_ERROR")))
        .andExpect(jsonPath("$.code").value(containsString("DUPLICATE_CURRENCY_CODE")));
    // ✅ No assertion on $.message - it's subject to change
}

@Test
void shouldReturn404ForNonExistentId() throws Exception {
    mockMvc.perform(get("/v1/admin/currencies/{id}", 999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(containsString("NOT_FOUND")));
    // ✅ No assertion on $.message - it's subject to change
}

@Test
void shouldReturnValidationErrorForInvalidRequest() throws Exception {
    mockMvc.perform(post("/api/v1/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\": -100}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
    // ✅ No assertion on $.fieldErrors[0].message - it's subject to change
}

// ❌ BAD - Asserting on message text
@Test
void badExample() throws Exception {
    mockMvc.perform(get("/v1/admin/currencies/{id}", 999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Currency series not found with ID: 999"));
    // ❌ Message text is implementation detail, not contract
}
```

### Service Layer Exception Testing

**CRITICAL**: The same rule applies to service-layer exception tests. Assert on the **exception type only** — never on the exception message text. Exception types are part of the service contract; message text is implementation detail that will drift for clarity, i18n, or UX reasons.

**Stable contract** (safe to assert on):
- Exception type (`assertThatThrownBy(...).isInstanceOf(X.class)`)

**Unstable** (DO NOT assert on):
- Exception message text — including via `.hasMessage(...)`, `.hasMessageContaining(...)`, `.hasMessageMatching(...)`
- Exception `cause` message text
- Any wording embedded in the exception

AssertJ's `.hasMessageContaining(...)` is the most common vector for this anti-pattern — it looks innocuous because it's "just checking the ID is in the message", but it couples the test to the exact phrasing of the thrown exception and breaks every time the message is rewritten.

**Examples**

```java
// ✅ GOOD - Assert on exception type only
@Test
void shouldThrowResourceNotFoundExceptionWhenIdDoesNotExist() {
    when(repository.findByIdActive(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getById(999L))
        .isInstanceOf(ResourceNotFoundException.class);
}

// ❌ BAD - hasMessageContaining couples the test to message wording
@Test
void badExample() {
    when(repository.findByIdActive(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getById(999L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("999")           // ❌ message text is not contract
        .hasMessageContaining("not found");    // ❌ message text is not contract
}

// ❌ BAD - Asserting on message text
@Test
void badExample() {
    assertThatThrownBy(() -> service.getById(999L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found: 999"); // ❌ message text is not contract
}
```

**If you need to distinguish between two failure modes of the same exception type**, that is a signal to introduce a more specific exception type (or an error code on the existing one), not to grep the message. Exception subtypes and error codes are contract; strings are not.

### Why This Matters

1. **Maintainability**: Message text often changes for clarity, i18n, or UX improvements
2. **Flexibility**: Teams can refine error messages without breaking tests
3. **Contract focus**: Tests verify the programmatic contract, not human-readable text
4. **OpenAPI alignment**: Error types and codes are documented in OpenAPI specs; messages are not

See [AdminCurrencySeriesControllerTest.java](../../currency-service/src/test/java/org/budgetanalyzer/currency/api/AdminCurrencySeriesControllerTest.java) for real-world examples of this pattern.

## Performance Testing

### Testing with Large Datasets

```java
@Test
void shouldHandleLargeDataset() {
    var transactions = IntStream.range(0, 10000)
        .mapToObj(i -> createTransaction("100.00", "Transaction " + i))
        .toList();

    var startTime = System.currentTimeMillis();
    service.processTransactions(transactions);
    var duration = System.currentTimeMillis() - startTime;

    // Should process 10k transactions in under 5 seconds
    assertThat(duration).as("Processing took too long: %dms", duration).isLessThan(5000);
}
```

## Test Organization

### Directory Structure
```
src/test/java/
└── org/budgetanalyzer/{service}/
    ├── controller/
    │   └── TransactionControllerTest.java
    ├── service/
    │   └── TransactionServiceTest.java
    │   └── TransactionServiceIntegrationTest.java
    │   └── provider/
    │       └── ExchangeRateProviderIntegrationTest.java
    ├── repository/
    │   └── TransactionRepositoryIntegrationTest.java
    └── util/
        └── TransactionTestFixtures.java
```

## Running Tests

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test Class
```bash
./gradlew test --tests TransactionServiceTest
```

### Run Specific Test Method
```bash
./gradlew test --tests TransactionServiceTest.shouldCreateTransaction
```

### Run Tests with Coverage
```bash
./gradlew test jacocoTestReport
```

### Skip Tests (for quick builds)
```bash
./gradlew build -x test
```

## Common Testing Pitfalls

### 1. Brittle Tests
```java
// ❌ BAD - Will break if order changes
assertThat(users.get(0).getName()).isEqualTo("John");

// ✅ GOOD - Tests what matters
assertThat(users).extracting("name").contains("John");
```

### 2. Testing Implementation Details
```java
// ❌ BAD - Testing private method behavior
@Test
void shouldCallPrivateMethod() { }

// ✅ GOOD - Test public API behavior
@Test
void shouldReturnCorrectResult() { }
```

### 3. Not Cleaning Up After Tests
```java
@AfterEach
void tearDown() {
    repository.deleteAll();
    MDC.clear();
}
```

## Resources

- [AssertJ Documentation](https://assertj.github.io/doc/) (assertion library)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/) (test runner)
- [Spring Boot Testing Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [TestContainers Documentation](https://testcontainers.com/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)

---

## External Links (GitHub Web Viewing)

*The relative paths in this document are optimized for Claude Code. When viewing on GitHub, use this link to access the referenced test file:*

- [AdminCurrencySeriesControllerTest.java](https://github.com/budgetanalyzer/currency-service/blob/main/src/test/java/org/budgetanalyzer/currency/api/AdminCurrencySeriesControllerTest.java)
