# Advanced Patterns - Budget Analyzer Spring Boot Services

## Overview

This document describes advanced architectural patterns used across Budget Analyzer microservices. These patterns address production concerns like external integrations, distributed systems coordination, caching, and reliable messaging.

**When to use these patterns:**
- Provider abstraction: When integrating with external data sources (APIs, data feeds)
- Event-driven messaging: When services need to communicate asynchronously with guaranteed delivery
- Distributed caching: When query performance is critical and data is read-heavy
- Distributed locking: When scheduled tasks must run once across multiple pods
- Database migrations: For all services with persistent storage (always use Flyway)

---

## Provider Abstraction Pattern

**Purpose**: Decouple services from specific external data source implementations.

### Problem

Direct dependencies on external providers create tight coupling:
```java
// ❌ Service tightly coupled to specific provider
@Service
public class DataService {
    private final FredClient fredClient;  // Locked to FRED forever

    public Data getData() {
        return fredClient.fetch();  // Can't switch providers
    }
}
```

### Solution

Use interface-based provider abstraction:

```
service/
  ├── service/DataService.java           (uses provider interface)
  └── service/provider/
      ├── DataProvider.java               (interface)
      └── FredDataProvider.java           (implementation)
client/
  └── fred/FredClient.java                (provider-specific HTTP client)
```

### Pattern Components

**1. Provider Interface** (service layer):
```java
public interface ExchangeRateProvider {
    Map<LocalDate, BigDecimal> getExchangeRates(CurrencySeries series, LocalDate startDate);
    boolean validateSeriesExists(String providerSeriesId);
}
```

**2. Provider Implementation** (service/provider/):
```java
@Service
@Primary  // If multiple implementations exist
public class FredExchangeRateProvider implements ExchangeRateProvider {
    private final FredClient fredClient;  // Provider-specific client

    @Override
    public Map<LocalDate, BigDecimal> getExchangeRates(
            CurrencySeries series, LocalDate startDate) {
        // Call FRED API, transform to generic format
        var response = fredClient.fetchSeries(series.getProviderSeriesId());
        return transformToMap(response);
    }
}
```

**3. Service Layer** (uses interface only):
```java
@Service
public class CurrencyService {
    private final ExchangeRateProvider provider;  // Interface dependency

    public void validateSeries(String providerSeriesId) {
        if (!provider.validateSeriesExists(providerSeriesId)) {
            throw new BusinessException(
                "Series not found in external provider",  // Generic message
                "PROVIDER_SERIES_NOT_FOUND");
        }
    }
}
```

### Dependency Rules

✅ **Allowed:**
- `Service` → `ExchangeRateProvider` (interface)
- `FredExchangeRateProvider` → `FredClient`
- Multiple implementations with `@Primary` or `@Qualifier`

❌ **Forbidden:**
- `Service` → `FredClient` (concrete provider client)
- `Service` → `FredExchangeRateProvider` (concrete implementation)
- Provider name ("FRED") in service layer error messages

### Benefits

- **Extensibility**: Add new providers without changing services
- **Testability**: Mock provider interface for unit tests
- **Substitutability**: Switch providers via configuration
- **Encapsulation**: Provider-specific logic contained in implementation

### Example: Adding Second Provider

```java
@Service
@Qualifier("ecb")
public class EcbExchangeRateProvider implements ExchangeRateProvider {
    private final EcbClient ecbClient;

    // Same interface, different implementation
}

// Service unchanged - works with both providers
```

---

## Event-Driven Messaging with Transactional Outbox

**Purpose**: Guarantee message delivery using database-backed event persistence.

### The Dual-Write Problem

Traditional message publishing has race conditions:
```java
@Transactional
public void create(Currency currency) {
    repository.save(currency);           // Write #1: Database
    messagePublisher.publish(message);   // Write #2: RabbitMQ
    // If app crashes between these? Message lost!
    // If RabbitMQ down? Database saved but no event!
}
```

### Solution: Spring Modulith Transactional Outbox

Spring Modulith stores events in the database **within the same transaction** as business data:

```java
@Transactional
public void create(Currency currency) {
    var saved = repository.save(currency);         // Write #1
    eventPublisher.publishEvent(new Event(...));   // Write #2 - same transaction!
    // Both succeed or both fail - atomic guarantee
}
// After commit, Spring Modulith asynchronously publishes to RabbitMQ
```

### Architecture

```
HTTP Request (Thread 1)
  └─> Service.create()
      ├─> repository.save(entity)              [Transaction]
      ├─> eventPublisher.publishEvent(...)     [Same transaction]
      └─> Spring Modulith stores in event_publication table

Background (Thread 2)
  └─> Spring Modulith polls event_publication
      ├─> Invokes @ApplicationModuleListener
      ├─> Listener publishes to RabbitMQ
      └─> Marks event completed
```

### Pattern Components

**1. Domain Events** (domain/event/):
```java
/**
 * Domain event published when a currency series is created.
 * Spring Modulith automatically persists to event_publication table.
 */
public record CurrencyCreatedEvent(
    Long currencySeriesId,
    String currencyCode,
    String correlationId
) {}
```

**2. Service Layer** (publishes events):
```java
@Service
public class CurrencyService {
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CurrencySeries create(CurrencySeries series) {
        var saved = repository.save(series);

        // Publish domain event - persisted in same transaction
        var correlationId = MDC.get("correlationId");
        eventPublisher.publishEvent(
            new CurrencyCreatedEvent(saved.getId(), saved.getCurrencyCode(), correlationId)
        );

        return saved;  // Commits both entity AND event atomically
    }
}
```

**3. Event Listeners** (messaging/listener/):
```java
@Component
public class MessagingEventListener {
    private final CurrencyMessagePublisher messagePublisher;

    /**
     * @ApplicationModuleListener enables transactional outbox:
     * - Event already persisted before this runs
     * - Runs asynchronously after transaction commits
     * - Automatically retries on failure
     * - Marked complete after successful processing
     */
    @ApplicationModuleListener
    void onCurrencyCreated(CurrencyCreatedEvent event) {
        MDC.put("correlationId", event.correlationId());
        try {
            messagePublisher.publishCurrencyCreated(
                new CurrencyCreatedMessage(
                    event.currencySeriesId(),
                    event.currencyCode(),
                    event.correlationId()
                )
            );
        } finally {
            MDC.clear();
        }
    }
}
```

**4. Message Publishers** (messaging/publisher/):
```java
@Component
public class CurrencyMessagePublisher {
    private final StreamBridge streamBridge;

    public void publishCurrencyCreated(CurrencyCreatedMessage message) {
        streamBridge.send("currencyCreated-out-0", message);
    }
}
```

**5. Message Consumers** (messaging/consumer/):
```java
@Configuration
public class ExchangeRateImportConsumer {
    private final ExchangeRateImportService service;

    @Bean
    public Consumer<CurrencyCreatedMessage> importExchangeRates() {
        return message -> {
            MDC.put("correlationId", message.correlationId());
            try {
                service.importExchangeRatesForSeries(message.currencySeriesId());
            } finally {
                MDC.clear();  // Always clean up MDC
            }
            // Exceptions propagate to Spring Cloud Stream retry
        };
    }
}
```

### Consumer Error Handling

**CRITICAL**: Consumers MUST NOT swallow exceptions. Spring Cloud Stream retry depends on exceptions propagating.

```java
// ❌ WRONG - Breaks retry mechanism
@Bean
public Consumer<Message> process() {
    return message -> {
        try {
            service.process(message);
        } catch (Exception e) {
            log.error("Failed", e);  // Exception swallowed!
            // Spring thinks it succeeded - no retry!
        }
    };
}

// ✅ CORRECT - Allows retry
@Bean
public Consumer<Message> process() {
    return message -> {
        MDC.put("correlationId", message.correlationId());
        try {
            service.process(message);
        } finally {
            MDC.clear();  // Cleanup always runs
        }
        // Exception propagates naturally
    };
}
```

### Configuration

**Database Schema:**
```sql
CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP(6) WITH TIME ZONE     -- NULL = pending
);

CREATE INDEX idx_event_publication_unpublished
    ON event_publication(completion_date)
    WHERE completion_date IS NULL;
```

**Application Configuration:**
```yaml
spring:
  modulith:
    events:
      republish-outstanding-events-on-restart: true
      delete-completion-after: 30d

  cloud:
    function:
      definition: importExchangeRates
    stream:
      bindings:
        currencyCreated-out-0:
          destination: currency.created
        importExchangeRates-in-0:
          destination: currency.created
          group: exchange-rate-import-service
      rabbit:
        bindings:
          importExchangeRates-in-0:
            consumer:
              auto-bind-dlq: true
              republish-to-dlq: true
              max-attempts: 3
              initial-interval: 1000
              multiplier: 2.0
```

### Benefits

1. **100% Guaranteed Delivery** - Events persisted in database with business data
2. **Exactly-Once Semantics** - Eliminates dual-write problem
3. **Async HTTP Responses** - Request completes after DB commit, not RabbitMQ publish
4. **Automatic Retries** - Spring Modulith retries failed events until successful
5. **Event Replay** - All events stored for troubleshooting and replay
6. **Audit Trail** - Complete history of domain events with timestamps

### Distributed Tracing

Correlation ID flows through entire pipeline:
```
HTTP → CorrelationIdFilter validates or regenerates correlation ID, then sets MDC
  → Service includes in domain event
    → Event listener sets MDC from event
      → External message includes correlationId
        → Consumer sets MDC from message
```

All logs include same correlation ID for end-to-end tracing.

---

## Redis Distributed Caching

**Purpose**: Improve query performance with shared cache across application instances.

### When to Use

- **Read-heavy workloads**: 10:1 or higher read-to-write ratio
- **Expensive queries**: Database queries taking >50ms
- **Multi-instance deployment**: Cache must be shared across pods
- **Immediate consistency required**: Financial data that must be consistent after updates

### Architecture

```
Request → Service
  ↓ (cache miss)
  └─> Repository → Database (50-200ms)
      └─> Cache result in Redis

Next Request → Service
  ↓ (cache hit)
  └─> Redis (1-3ms) ← 50-200x faster!
```

### Configuration

**Cache Config Class:**
```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfig())
            .withCacheConfiguration("exchangeRates", exchangeRatesCacheConfig())
            .build();
    }

    private RedisCacheConfiguration defaultCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeKeysWith(/* JSON serialization */)
            .serializeValuesWith(/* JSON serialization */)
            .computePrefixWith(cacheName -> "currency-service:" + cacheName + ":");
    }
}
```

**Application Properties:**
```yaml
spring:
  cache:
    type: redis
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}  # Empty for no auth
```

### Service Layer Annotations

**Cacheable (Query):**
```java
@Service
public class ExchangeRateService {

    @Cacheable(
        value = "exchangeRates",
        key = "#targetCurrency + ':' + #startDate + ':' + #endDate"
    )
    public List<ExchangeRateData> getExchangeRates(
            Currency targetCurrency, LocalDate startDate, LocalDate endDate) {
        // Expensive database query - cached after first call
        return repository.findByTargetCurrency(targetCurrency, startDate, endDate);
    }
}
```

**Cache Evict (Update/Import):**
```java
@Service
public class ExchangeRateImportService {

    @CacheEvict(value = "exchangeRates", allEntries = true)
    public void importLatestExchangeRates() {
        // Import new rates
        // Clear ALL cache entries to ensure consistency
    }
}
```

### Cache Key Design

**Pattern**: Include all query parameters that affect results:
```
{targetCurrency}:{startDate}:{endDate}
Example: "USD:2025-01-01:2025-01-31"
```

**Why JSON Serialization?**
- Human-readable in Redis CLI for debugging
- No version conflicts (unlike Java serialization)
- Supports different language clients

### Performance Characteristics

| Metric | Value |
|--------|-------|
| Cache hit response time | 1-3ms |
| Cache miss response time | 50-200ms |
| Expected hit rate | 80-95% |
| Memory per 10K queries | ~500MB |

### Why Redis (not Caffeine)?

- **Shared state**: All pods see same cache data
- **Immediate consistency**: Cache invalidation affects all instances
- **Production standard**: Industry-proven distributed cache
- **No pub/sub needed**: Simple eviction pattern with `allEntries = true`

---

## Distributed Locking with ShedLock

**Purpose**: Ensure scheduled tasks run once across multiple application instances.

### Problem

Without distributed locking, every pod executes scheduled tasks:
```java
@Scheduled(cron = "0 0 23 * * *")  // 11 PM daily
public void importExchangeRates() {
    // In 3-pod deployment, this runs 3 times!
    // - Duplicate API calls
    // - Race conditions in database
    // - Cache thrashing
}
```

### Solution: ShedLock

Database-backed distributed lock coordination:

```java
@Scheduled(cron = "0 0 23 * * *")
@SchedulerLock(
    name = "exchangeRateImport",
    lockAtMostFor = "15m",    // Safety timeout
    lockAtLeastFor = "1m"      // Minimum hold time
)
public void importExchangeRates() {
    // Only ONE pod acquires lock and executes
    // Others skip silently
}
```

### How It Works

1. Scheduler triggers on all pods
2. Each pod attempts database write to `shedlock` table
3. One pod succeeds (database constraint), others fail
4. Winner executes task
5. Lock auto-expires after `lockAtMostFor` duration

### Configuration

**ShedLock Config Class:**
```java
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
```

**Database Migration:**
```sql
-- Flyway migration: V2__add_shedlock_table.sql
CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
```

**Dependencies:**
```kotlin
// build.gradle.kts
implementation("net.javacrumbs.shedlock:shedlock-spring:5.2.0")
implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.2.0")
```

### Lock Duration Guidelines

**`lockAtMostFor`** (safety timeout):
- Set longer than expected task duration
- Prevents deadlocks if pod crashes
- Example: 15 minutes for 30-second task

**`lockAtLeastFor`** (minimum hold):
- Prevents rapid re-execution
- Example: 1 minute prevents double-execution on quick completion

### Why JDBC/PostgreSQL (not Redis)?

- **Service independence**: Each microservice uses own database
- **Better isolation**: Database outage affects one service, not all
- **Simpler operations**: No separate Redis dependency for locking
- **Sufficient performance**: Lock once per day - 10-50ms latency acceptable

### Monitoring

**Check lock status:**
```sql
SELECT * FROM shedlock WHERE name = 'exchangeRateImport';
```

**Verify execution:**
- Application logs: "Starting scheduled exchange rate import" indicates lock acquired
- Metrics: Custom Micrometer counter for execution count

---

## Database Migrations with Flyway

**Purpose**: Version-controlled, repeatable database schema evolution.

### Why Flyway?

- **Version control**: Schema changes tracked in Git with code
- **Repeatable**: Same migrations run identically across environments
- **Audit trail**: Complete history of schema changes
- **Team coordination**: Prevents conflicting schema modifications
- **Rollback documentation**: Undo scripts provide rollback instructions

### Configuration

**Application Properties:**
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # JPA validates, never modifies schema
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true
```

**Dependencies:**
```kotlin
// build.gradle.kts
implementation("org.flywaydb:flyway-core")
runtimeOnly("org.flywaydb:flyway-database-postgresql")
```

### Migration Workflow

**1. Create Migration File:**
```
src/main/resources/db/migration/
  ├── V1__initial_schema.sql
  ├── V2__add_shedlock_table.sql
  └── V3__add_exchange_rate_source_column.sql
```

**Naming Convention:**
- Versioned: `V{version}__{description}.sql` (e.g., `V1__initial_schema.sql`)
- Undo (manual): `U{version}__rollback_{description}.sql` (documentation only)

**2. Write SQL:**
```sql
-- V3__add_exchange_rate_source_column.sql
ALTER TABLE exchange_rate ADD COLUMN source VARCHAR(50);
COMMENT ON COLUMN exchange_rate.source IS 'Data source (e.g., FRED, ECB)';
```

**3. Test Migration:**
```bash
# Test on clean database
./gradlew cleanTest test

# Test on existing database
./gradlew bootRun
```

**4. Commit:**
```bash
git add src/main/resources/db/migration/V3__*.sql
git commit -m "feat: add exchange rate source column"
```

### Important Rules

- **Never modify committed migrations** - Create new migrations instead
- **Test with clean database** - Use `cleanTest` to verify from-scratch execution
- **JPA validates schema** - Entities must match migrated schema (`ddl-auto: validate`)
- **Undo migrations** - For documentation; not auto-executed (Flyway free tier)

### Temporal Column Types

**CRITICAL**: Always use `TIMESTAMP(6) WITH TIME ZONE` for all timestamp columns to ensure consistency across all services.

**Standard pattern**:
```sql
-- For entities extending AuditableEntity/SoftDeletableEntity
created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
updated_at TIMESTAMP(6) WITH TIME ZONE,
deleted_at TIMESTAMP(6) WITH TIME ZONE,

-- For temporal/audit tables (not extending base entities)
granted_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
revoked_at TIMESTAMP(6) WITH TIME ZONE,
expires_at TIMESTAMP(6) WITH TIME ZONE,
```

**Why this pattern?**
- **Precision (6)**: Microsecond precision for accurate audit trails
- **WITH TIME ZONE**: Timezone-aware storage prevents issues across time zones
- **Consistency**: All services use identical types for interoperability

**Note**: Entities extending `AuditableEntity` or `SoftDeletableEntity` should NOT use `DEFAULT CURRENT_TIMESTAMP` since timestamps are set by JPA lifecycle callbacks.

### Example: Adding New Table

```sql
-- V4__add_currency_conversion_cache.sql
CREATE TABLE currency_conversion_cache (
    id BIGSERIAL PRIMARY KEY,
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    conversion_date DATE NOT NULL,
    rate DECIMAL(19, 8) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_conversion_cache UNIQUE (from_currency, to_currency, conversion_date)
);

CREATE INDEX idx_conversion_cache_from ON currency_conversion_cache(from_currency);
CREATE INDEX idx_conversion_cache_date ON currency_conversion_cache(conversion_date);

COMMENT ON TABLE currency_conversion_cache IS 'Cached currency conversion rates';
```

### Rollback Strategy

Create undo migration for documentation:
```sql
-- U4__rollback_currency_conversion_cache.sql
DROP TABLE IF EXISTS currency_conversion_cache;
```

**Manual rollback:**
1. Stop application
2. Execute undo SQL manually in database
3. Delete migration record from `flyway_schema_history`
4. Restart application

---

## Pattern Decision Matrix

| Pattern | Use When | Don't Use When |
|---------|----------|----------------|
| **Provider Abstraction** | Integrating external APIs, may switch providers | Single provider, internal services only |
| **Transactional Outbox** | Async communication between services, guaranteed delivery required | Simple request-response, no messaging needed |
| **Redis Caching** | Read-heavy (10:1), expensive queries (>50ms), multi-pod deployment | Write-heavy, single instance, fast queries |
| **ShedLock** | Scheduled tasks in multi-pod deployment | Single instance deployment, no scheduled tasks |
| **Flyway Migrations** | ANY service with database | Stateless services, no persistence |

---

## Service Implementation Examples

**Currency Service** (uses ALL patterns):
- Provider abstraction: FRED API integration
- Transactional outbox: Publishes currency created events
- Redis caching: Exchange rate query caching
- ShedLock: Daily exchange rate import scheduler
- Flyway: Schema versioning

**Transaction Service** (uses subset):
- Flyway: Schema versioning (when implemented)
- Redis caching: Transaction search results (future)
- Provider abstraction: N/A (no external providers)
- Transactional outbox: N/A (no async messaging yet)
- ShedLock: N/A (no scheduled tasks)

---

## Related Documentation

- Spring Boot conventions: [spring-boot-conventions.md](spring-boot-conventions.md)
- Error handling: [error-handling.md](error-handling.md)
- Testing patterns: [testing-patterns.md](testing-patterns.md)
- Code quality: [code-quality-standards.md](code-quality-standards.md)
