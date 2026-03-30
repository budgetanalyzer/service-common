# Error Handling - Standardized API Error Responses

## Overview

All Budget Analyzer microservices use a standardized error response format provided by the **service-web** module.

**Module**: service-web (from service-common multi-module project)

## Standard Error Response Format

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

## Error Response Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `ApiErrorType` enum | Yes | Error type enumeration |
| `message` | String | Yes | Human-readable error description |
| `code` | String | No | Machine-readable error code (for `APPLICATION_ERROR`) |
| `fieldErrors` | Array | No | Field-level validation errors (for `VALIDATION_ERROR`) |

## Error Types

The `ApiErrorType` enum defines standard error categories:

| Type | HTTP Status | Use Case | Includes Code | Includes Field Errors |
|------|-------------|----------|---------------|---------------------|
| `INVALID_REQUEST` | 400 | Malformed request or bad syntax | No | No |
| `VALIDATION_ERROR` | 400 | Field validation failed | No | Yes |
| `NOT_FOUND` | 404 | Requested resource does not exist | No | No |
| `APPLICATION_ERROR` | 422 | Business rule violation | Yes | No |
| `SERVICE_UNAVAILABLE` | 503 | Downstream service unavailable | No | No |
| `INTERNAL_ERROR` | 500 | Unexpected server error | No | No |

## Exception Hierarchy

Service-common provides a standardized exception hierarchy:

```
Throwable
└── RuntimeException
    ├── ServiceException (500 - Internal Server Error)
    │   ├── ServiceUnavailableException (503 - Service Unavailable)
    │   └── [other internal errors]
    └── ClientException (400 - Bad Request)
        ├── ResourceNotFoundException (404 - Not Found)
        ├── InvalidRequestException (400 - Bad Request)
        └── BusinessException (422 - Unprocessable Entity)
```

### Exception Details

#### ServiceException
- **HTTP Status**: 500 Internal Server Error
- **Error Type**: `INTERNAL_ERROR`
- **Use Case**: Unexpected server errors, system failures

#### ClientException
- **HTTP Status**: 400 Bad Request
- **Error Type**: Varies by subclass
- **Use Case**: Base for all client-caused errors

#### ResourceNotFoundException
- **HTTP Status**: 404 Not Found
- **Error Type**: `NOT_FOUND`
- **Use Case**: Requested entity doesn't exist

**Example**:
```java
@GetMapping("/{id}")
public TransactionResponse getById(@PathVariable Long id) {
    return repository.findByIdActive(id)
        .map(mapper::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
}
```

#### InvalidRequestException
- **HTTP Status**: 400 Bad Request
- **Error Type**: `INVALID_REQUEST`
- **Use Case**: Malformed request, invalid syntax, missing required fields

**Example**:
```java
public Transaction processImport(MultipartFile file) {
    if (file.isEmpty()) {
        throw new InvalidRequestException("File cannot be empty");
    }
    // process file
}
```

#### BusinessException
- **HTTP Status**: 422 Unprocessable Entity
- **Error Type**: `APPLICATION_ERROR`
- **Use Case**: Business rule violations with application-specific error codes
- **Special**: Includes `code` field for machine-readable error identification

**Example**:
```java
public Transaction createTransaction(TransactionRequest request) {
    if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        throw new BusinessException(
            "INVALID_AMOUNT",
            "Transaction amount must be positive"
        );
    }

    if (isDuplicate(request)) {
        throw new BusinessException(
            "DUPLICATE_TRANSACTION",
            "A transaction with these details already exists"
        );
    }

    // process transaction
}
```

Response:
```json
{
  "type": "APPLICATION_ERROR",
  "message": "Transaction amount must be positive",
  "code": "INVALID_AMOUNT"
}
```

#### ServiceUnavailableException
- **HTTP Status**: 503 Service Unavailable
- **Error Type**: `SERVICE_UNAVAILABLE`
- **Use Case**: External service failures, circuit breaker open, timeout

**Example**:
```java
public ExchangeRate getExchangeRate(String currencyPair) {
    try {
        return externalApiClient.fetchRate(currencyPair);
    } catch (Exception e) {
        throw new ServiceUnavailableException(
            "Currency exchange service temporarily unavailable",
            e
        );
    }
}
```

## Global Exception Handler

The **service-web** module provides `ServletApiExceptionHandler` - a `@RestControllerAdvice` that automatically converts all exceptions to standardized `ApiErrorResponse` format.

**Location**: `service-web/src/main/java/org/budgetanalyzer/service/servlet/api/ServletApiExceptionHandler.java`

### How It Works

1. **Include service-web dependency** in your microservice (`org.budgetanalyzer:service-web`)
2. **Enable component scanning** for `org.budgetanalyzer.service` package
3. **Throw exceptions** from your controllers/services
4. **Handler automatically converts** to `ApiErrorResponse`

### Exception Mapping Table

| Exception Class | HTTP Status | Error Type | Additional Fields |
|----------------|-------------|------------|-------------------|
| `ResourceNotFoundException` | 404 | `NOT_FOUND` | None |
| `InvalidRequestException` | 400 | `INVALID_REQUEST` | None |
| `BusinessException` | 422 | `APPLICATION_ERROR` | `code` |
| `ServiceUnavailableException` | 503 | `SERVICE_UNAVAILABLE` | None |
| `ClientException` | 503 | `SERVICE_UNAVAILABLE` | None |
| `ServiceException` | 500 | `INTERNAL_ERROR` | None |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` | `fieldErrors` array |
| `AccessDeniedException` | 403 | `FORBIDDEN` | None |
| `AuthorizationDeniedException` | 403 | `FORBIDDEN` | None |
| `AuthenticationException` | 401 | `UNAUTHORIZED` | None |
| `ResponseStatusException` | Preserved | Mapped from status | None |
| Any other `Exception` | 500 | `INTERNAL_ERROR` | None |

### Bean Validation Integration

Spring's `@Valid` annotation automatically triggers `MethodArgumentNotValidException`, which the handler converts to a `VALIDATION_ERROR` response with field-level details.

**Example**:
```java
@PostMapping
public TransactionResponse create(@Valid @RequestBody CreateTransactionRequest request) {
    // If validation fails, handler returns:
    // {
    //   "type": "VALIDATION_ERROR",
    //   "message": "Validation failed for one or more fields",
    //   "fieldErrors": [
    //     {
    //       "field": "amount",
    //       "rejectedValue": "-100",
    //       "message": "must be greater than 0"
    //     }
    //   ]
    // }
}
```

### ResponseStatusException Handling

`ResponseStatusException` is handled specially: the original HTTP status code is preserved rather than being mapped to a fixed status. The error type is derived from the status code:

| Status Code | Error Type |
|-------------|------------|
| 401 | `UNAUTHORIZED` |
| 403 | `FORBIDDEN` |
| 404 | `NOT_FOUND` |
| 503 | `SERVICE_UNAVAILABLE` |
| Other 4xx | `INVALID_REQUEST` |
| Other 5xx | `INTERNAL_ERROR` |

The exception's `reason` is passed as the response message (it is developer-controlled, not an internal detail leak).

Without this handler, `ResponseStatusException` would fall through to the generic `Exception` catch-all and incorrectly return HTTP 500 for all status codes.

Both `ServletApiExceptionHandler` and `ReactiveApiExceptionHandler` support this.

## Best Practices

### 1. Choose the Right Exception

- **404 scenarios**: Use `ResourceNotFoundException`
- **Invalid input format**: Use `InvalidRequestException`
- **Business rule violations**: Use `BusinessException` with error code
- **External service failures**: Use `ServiceUnavailableException`
- **Unexpected errors**: Let them fall through to `ServiceException`

### 2. Provide Meaningful Messages

```java
// ❌ BAD - Vague message
throw new ResourceNotFoundException("Not found");

// ✅ GOOD - Specific, actionable message
throw new ResourceNotFoundException("Transaction not found with id: " + id);
```

### 3. Use Error Codes for Business Exceptions

```java
// ❌ BAD - No error code for business logic error
throw new BusinessException("Insufficient funds");

// ✅ GOOD - Error code enables programmatic handling
throw new BusinessException("INSUFFICIENT_FUNDS", "Account balance is insufficient for this transaction");
```

### 4. Don't Expose Internal Details

```java
// ❌ BAD - Exposes database/internal details
throw new ServiceException("SQLException: duplicate key value violates unique constraint");

// ✅ GOOD - User-friendly message
throw new BusinessException("DUPLICATE_ENTITY", "A transaction with this reference already exists");
```

### 5. Log Exceptions Appropriately

```java
@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    public Transaction process(Transaction transaction) {
        try {
            return externalService.validate(transaction);
        } catch (Exception e) {
            // Log with context before throwing
            log.error("Failed to validate transaction {}: {}", transaction.getId(), e.getMessage(), e);
            throw new ServiceUnavailableException("Validation service unavailable", e);
        }
    }
}
```

## Benefits

### Consistency
- All microservices return errors in the same format
- Frontend can handle errors uniformly
- API consumers have predictable error structure

### Machine-Readable
- Error types enable programmatic handling
- Error codes for business logic errors
- Field-level validation details

### Client-Friendly
- Clear, actionable error messages
- Field-level validation feedback
- Appropriate HTTP status codes

### Maintainability
- Centralized error handling logic
- Easy to add new exception types
- Consistent across entire ecosystem
