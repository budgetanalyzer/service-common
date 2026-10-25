# Advanced Patterns - Budget Analyzer Spring Boot Services

## Overview

This document describes advanced architectural patterns used across Budget Analyzer microservices. These patterns address production concerns like external integrations, distributed systems coordination, caching, and reliable messaging.

**When to use these patterns:**
- Provider abstraction: When integrating with external data sources (APIs, data feeds)
- Event-driven messaging: When services need to communicate asynchronously and the intent to send must be recorded durably and atomically with the business change
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
- **Testability**: Exercise the real provider bean against WireMock at the external HTTP boundary
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

**Purpose**: Record the intent to send a message durably and atomically with the business data, then
send it after the transaction commits.

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
// After commit, Spring Modulith invokes the listener asynchronously; the listener sends to RabbitMQ
```

### Architecture

```
HTTP Request (Thread 1)
  └─> Service.create()
      ├─> repository.save(entity)              [Transaction]
      ├─> eventPublisher.publishEvent(...)     [Same transaction]
      └─> Spring Modulith stores in event_publication table

After commit (task executor thread)
  └─> Spring Modulith invokes @ApplicationModuleListener asynchronously
      ├─> Listener publishes to RabbitMQ (StreamBridge.send)
      └─> Spring Modulith marks the publication COMPLETED when the listener returns
          (a listener that throws leaves it FAILED)
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
     * - Marked COMPLETED when this method returns
     * - If this method throws, the publication stays FAILED and incomplete;
     *   nothing retries it automatically
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

### Publication Lifecycle (Spring Modulith 2.x)

The event publication registry stores one `event_publication` row per event and listener. Sources:
[Spring Modulith events reference](https://docs.spring.io/spring-modulith/reference/events.html)
and [appendix](https://docs.spring.io/spring-modulith/reference/appendix.html).

**Statuses** (`status` column):

| Status | Meaning |
|--------|---------|
| `PUBLISHED` | Stored with the business transaction; waiting for the listener |
| `PROCESSING` | A listener claimed the publication and is running |
| `COMPLETED` | The listener returned without throwing |
| `FAILED` | The listener threw, or the staleness monitor marked the publication failed |
| `RESUBMITTED` | Application code resubmitted a failed publication; it is pending again |

`completion_attempts` starts at `1` when the publication is created and increments each time
application code resubmits it. Moving a publication to `PROCESSING` does not change the counter, so
it records publication and resubmission attempts rather than listener invocations.

**Completion modes** (`spring.modulith.events.completion-mode`):
- `update` (default): sets `completion_date` and keeps the row. The table grows by one row per
  publication, so purge completed publications (`CompletedEventPublications`) or accept the growth
  deliberately and document that decision in the service.
- `delete`: removes the row on completion. No audit trail remains, and `CompletedEventPublications`
  returns nothing.
- `archive`: moves the completed row to an archive table with the same schema.

**Staleness monitor** (`spring.modulith.events.staleness.*`): off by default. The per-state
timeouts `published`, `processing`, and `resubmitted` all default to `Duration.ZERO`, and the monitor
registers no task until at least one is non-zero (`check-interval` defaults to one minute). When
active, it marks publications older than their state's timeout `FAILED`. It never re-invokes a
listener. Before 2.1.2, a state whose timeout was zero was treated as immediately stale; 2.1.2 skips
states without a timeout
([spring-modulith#1918](https://github.com/spring-projects/spring-modulith/issues/1918)).

**Resubmission is application code only**:
- `FailedEventPublications.resubmit(ResubmissionOptions)` resubmits `FAILED` publications, and
  `IncompleteEventPublications` resubmits incomplete ones
  (`resubmitIncompletePublications(ResubmissionOptions)` plus predicate- and duration-based
  overloads). Only application code calls these APIs.
- Spring Modulith does not retry failed publications automatically.
  [spring-modulith#1916](https://github.com/spring-projects/spring-modulith/issues/1916)
  (milestone 2.2.0-RC1) keeps resubmission in application code, so 2.2 still leaves retrying to
  the application.
- 2.1.2 fixed the minimum-age query that the JDBC and JPA repositories ignored
  ([#1942](https://github.com/spring-projects/spring-modulith/issues/1942)) and the claiming of
  completed publications for resubmission
  ([#1935](https://github.com/spring-projects/spring-modulith/issues/1935)).
- Still open: a publication that cannot be deserialized stops a resubmission batch
  ([#1904](https://github.com/spring-projects/spring-modulith/issues/1904)), and concurrent
  resubmissions can claim a publication that another resubmission is processing, so the listener
  runs twice, even under a distributed lock
  ([#1971](https://github.com/spring-projects/spring-modulith/issues/1971)).

This guide does not recommend calling these APIs. See [Outbox Recovery](#outbox-recovery).

**What `COMPLETED` means**: the listener returned. For a RabbitMQ publisher, that proves the broker
accepted and routed the message only when the publisher waits for a publisher confirm and checks
for a returned message. The platform's outbox publishers do not configure publisher confirms or
returns yet. Today, a send fails the listener (leaving the publication `FAILED`) only when
`StreamBridge.send` throws, for example when the connection is unavailable. An unroutable message,
or a broker-side failure after `StreamBridge.send` returns, is recorded as `COMPLETED`.

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

Consumer retries and the dead-letter queue cover only messages that reached RabbitMQ. They never
retry a failed outbox send (listener → RabbitMQ); see
[Publication Lifecycle](#publication-lifecycle-spring-modulith-2x) and
[Outbox Recovery](#outbox-recovery). Retry settings and defaults are under
[Configuration](#configuration).

### Configuration

**Database Schema:**
```sql
CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP(6) WITH TIME ZONE,    -- NULL = pending
    status VARCHAR(20),
    completion_attempts INT NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMP(6) WITH TIME ZONE
);

CREATE INDEX idx_event_publication_unpublished
    ON event_publication(completion_date)
    WHERE completion_date IS NULL;
```

The table must match the columns the managed Spring Modulith version maps; compare it with the
current schema in the [Spring Modulith reference appendix](https://docs.spring.io/spring-modulith/reference/appendix.html).
With `ddl-auto: validate`, add new columns through a Flyway migration before upgrading Spring
Modulith.

Declare `completion_attempts` as `NOT NULL DEFAULT 0`, although the appendix schema leaves it
nullable. The JPA registry (`spring-modulith-starter-jpa`) maps it to a primitive `int` and cannot
load `NULL`. When migrating a Spring Modulith 1.x table, the default also backfills existing rows.

**Application Configuration:**
```yaml
spring:
  modulith:
    events:
      completion-mode: update  # Default: keep completed publications (alternatives: delete, archive)
      # Leave republish-outstanding-events-on-restart at its default (false); see Outbox Recovery

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
          # Retry uses the core consumer defaults. To override, add for example:
          # consumer:
          #   max-attempts: 5
      rabbit:
        bindings:
          importExchangeRates-in-0:
            consumer:
              auto-bind-dlq: true      # Bind a dead-letter queue
              republish-to-dlq: true   # Republish with exception details in headers
```

**Consumer retries:** Spring Cloud Stream's core consumer defaults apply: `maxAttempts` 3,
`backOffInitialInterval` 1000 ms, `backOffMultiplier` 2.0, and `backOffMaxInterval` 10000 ms (see
[binding properties](https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/binding-properties.html)).
Overrides belong under `spring.cloud.stream.bindings.<binding>.consumer.*`. Do not put retry keys
under `spring.cloud.stream.rabbit.bindings.<binding>.consumer`: the Rabbit consumer properties have
no retry fields, so the binder ignores them there. Keep `auto-bind-dlq` and `republish-to-dlq`
under the Rabbit binding. Retries and the DLQ apply only to messages that reached RabbitMQ and
never retry a failed outbox send.

### Outbox Recovery

Leave `spring.modulith.events.republish-outstanding-events-on-restart` at its default `false`:
- The [Spring Modulith appendix](https://docs.spring.io/spring-modulith/reference/appendix.html)
  says it is "usually not recommended in multi-instance deployments as other instances might still
  be processing events".
- In 1.4 and 2.1 alike, it resubmits every incomplete publication at startup, including ones
  another pod is processing. One publication that cannot be deserialized crash-loops startup
  ([spring-modulith#1904](https://github.com/spring-projects/spring-modulith/issues/1904)).
- [spring-modulith#1916](https://github.com/spring-projects/spring-modulith/issues/1916) proposes
  deprecating it in 2.2 in favor of application-written resubmission code.

**Recommended pattern: reconcile from database state.** Recover a lost trigger by re-deriving the
work from the data the service already owns, not by resubmitting publications. For example,
currency-service imports exchange rates for every enabled series at startup and daily at 23:00 UTC.
Each import starts after the latest stored rate, or loads the full history when none is stored, so
it covers every import request a failed or lost send would have triggered.

A service that cannot reconcile has no automatic recovery for a failed outbox send. Before adopting
the outbox, such a service must add a reconciliation path or document the gap as an accepted risk
in its own repository. This guide offers no resubmission job.

### Benefits

1. **No Dual-Write Problem** - The publication commits or rolls back with the business data; a
   rolled-back change never sends
2. **Durable Intent** - A crash after commit leaves the publication incomplete in
   `event_publication` instead of losing it
3. **Async HTTP Responses** - Request completes after DB commit, not RabbitMQ publish
4. **Audit Trail** (with `completion-mode: update`) - Completed publications stay with timestamps.
   `COMPLETED` means the listener returned, not that the broker confirmed the message (see
   [Publication Lifecycle](#publication-lifecycle-spring-modulith-2x))

Incomplete publications stay queryable in `event_publication` for diagnosis. This guide does not
recommend replaying them.

Whenever a reconciliation path exists, consumers must tolerate duplicate or overlapping triggers:
reconciliation and a delayed or redelivered message can request the same work.

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
- **Consistency after updates**: Evictions take effect for all pods when the write commits, but
  only with `immediateWrites()` and `transactionAware()`, and a racing read can still cache a stale
  result for up to the TTL (see [Configuration](#configuration-1))

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

**Cache Config Class** (Spring Boot 4, Spring Data Redis 4, Jackson 3):
```java
import java.time.Duration;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration(JsonMapper jsonMapper) {
        // Accept type ids only from these packages when reading cached values
        var typeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("org.budgetanalyzer")
            .allowIfSubType("java.util")
            .allowIfSubType("java.time")
            .allowIfSubType("java.math")
            .build();

        // Rebuild a copy so the application-wide JsonMapper is not modified
        var cacheJsonMapper = jsonMapper.rebuild()
            .activateDefaultTyping(typeValidator, DefaultTyping.NON_FINAL)
            .build();

        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .disableCachingNullValues()
            .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(SerializationPair.fromSerializer(
                new GenericJacksonJsonRedisSerializer(cacheJsonMapper)))
            .prefixCacheNameWith("currency-service:");  // "<service-name>:"
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(
            RedisConnectionFactory redisConnectionFactory) {
        return builder -> builder
            .cacheWriter(RedisCacheWriter.create(
                redisConnectionFactory, configurer -> configurer.immediateWrites()))
            .transactionAware();
    }
}
```

Why each setting matters (see the
[Spring Data Redis migration guide](https://docs.spring.io/spring-data/redis/reference/upgrading.html)
and [cache reference](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html)):
- **`immediateWrites()`**: When the connection factory implements `ReactiveRedisConnectionFactory`,
  as Lettuce's does, Spring Data Redis 4 performs `put`, `evict`, and `clear` asynchronously by
  default, so they may complete after the calling method returns. `immediateWrites()` makes them
  complete before the call returns.
- **No `UNLINK`**: The asynchronous `clear()` path issues `UNLINK`, which the platform's per-service
  Redis ACLs do not grant. The synchronous path uses `DEL`.
- **`transactionAware()`**: Defers `put`, `evict`, and `clear` until after the surrounding
  transaction commits, so a rolled-back write leaves the cache unchanged. `putIfAbsent`,
  `evictIfPresent`, and `invalidate` are not deferred.
- **Final types**: With `DefaultTyping.NON_FINAL`, final `java.*` types are written without a type
  id. JDK immutable collections are final and cannot be read back, so cacheable methods must not
  return `List.of()` or `Stream.toList()` results. Return `ArrayList` or `Collections.emptyList()`.
- **Properties ignored**: When the service defines its own `RedisCacheConfiguration`, Spring Boot
  ignores the `spring.cache.redis.*` entry settings (such as `time-to-live` and `key-prefix`); only
  `cache-names` and `enable-statistics` still apply.

**Always set a TTL**, even when every write path evicts. A cache-aside read whose query started
before a write committed can repopulate the cache with a stale result after the write's after-commit
eviction. Without a TTL, that entry lasts until the next eviction; the TTL bounds it. The TTL also
bounds memory for keys built from arbitrary parameters, such as date ranges. Do not enable
time-to-idle (`enableTimeToIdle()`): it reads with `GETEX`, which the service ACLs do not grant.

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

With `prefixCacheNameWith("currency-service:")`, the full Redis key is
`currency-service:exchangeRates::USD:2025-01-01:2025-01-31`.

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
- **Shared invalidation**: One eviction affects all instances (immediately after commit only with
  `immediateWrites()` and `transactionAware()`, and within the TTL bound for racing reads)
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
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
```

With `usingDbTime()`, ShedLock computes lock times in SQL from the database server clock in UTC
instead of each pod's clock, so clock skew between pods cannot shorten or extend a lock. The
[ShedLock README](https://github.com/lukas-krecan/ShedLock/blob/master/README.md) strongly
recommends it.

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
// build.gradle.kts (versions managed by spring-platform)
implementation("net.javacrumbs.shedlock:shedlock-spring")
implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template")
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
- Metrics (optional): add the `shedlock-micrometer` integration (ShedLock 7.7+). spring-platform
  does not manage this artifact, so declare it with the same version as the other ShedLock
  artifacts. ShedLock's Spring integration detects the listener bean automatically:
  ```java
  @Bean
  public LockingTaskExecutorListener shedLockMetricsListener(MeterRegistry meterRegistry) {
      return new MicrometerLockingTaskExecutorListener(meterRegistry);
  }
  ```
  It records metrics tagged with `lock.name`, such as `shedlock.lock.acquired`,
  `shedlock.lock.not.acquired`, and `shedlock.execution.duration`.

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
implementation("org.springframework.boot:spring-boot-starter-flyway")
runtimeOnly("org.flywaydb:flyway-database-postgresql")
```

Spring Boot 4 moved Flyway auto-configuration into its own module, so depend on the starter
rather than `org.flywaydb` core alone.

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
| **Transactional Outbox** | Async communication between services; durable, atomic recording of the intent to send required | Simple request-response, no messaging needed |
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
