# Spring Boot 4 Currency-Service Migration Plan

Migrate `currency-service` from Spring Boot 3.5.16 / service-common `0.0.17` to Spring Boot 4.1.2 /
service-common `0.0.18-SNAPSHOT`. Make only the changes required to compile, start, and pass the
existing currency-service test suite. General consumer caveats are in
[spring-boot-4-consumer-migration.md](spring-boot-4-consumer-migration.md). Use its "Findings From
Consumer Migrations" as established facts. The
[permission-service](spring-boot-4-permission-service-migration.md) and
[transaction-service](spring-boot-4-transaction-service-migration.md) plans are worked examples of
the same build and test relocations. Where those documents differ, this plan governs scope. In
particular, the consumer notes' Redis cache round-trip test and Modulith in-flight-event checks
appear below as rollout items only. This plan adds no tests for them.

Plan-wide constraints:

- Contract means the same endpoints, parameters, status codes, response fields, and values. JSON
  key order, whitespace, and OpenAPI formatting are not part of the contract.
- Do not add tests, snapshots, smoke checks, or any verification harness. The existing tests are
  the local verification. `../budget-analyzer-api-tests` is the verification after all services
  are migrated and deployed. You may augment an existing test to cover fragile behavior. This plan
  adds no such assertion: `createdAt`, `updatedAt`, and `timestamp` already have `isString()`
  assertions. Never modify, weaken, or delete existing assertions to make changes pass.
- Translate configuration like-for-like (renamed keys and classes only). Do not remove or redesign
  settings. The only exceptions are the two resolved decisions below.
- If the migration forces a behavior change, list it here. Do not build anything to detect or
  work around it.
- Do not read orchestration or api-tests internals.
- No git write operations. Leave all changes uncommitted.

Resolved decisions (2026-10-24, from the Phase 1 clarification stop):

- Redis cache writes stay synchronous. Spring Data Redis 4 makes the default `RedisCacheWriter`
  run `put`, `evict`, and `clear` asynchronously on Lettuce, as fire-and-forget calls whose
  failures are not logged. Spring Data Redis 3.5 was synchronous. currency-service is a blocking
  servlet service. Its import flow relies on `@CacheEvict(allEntries = true)` having cleared the
  cache, with no TTL, by the time the import returns. Four existing
  `ExchangeRateImportServiceIntegrationTest` assertions cover this. Step 6 therefore restores the
  3.x behavior with `immediateWrites()`, as the Spring Data Redis 4 upgrade notes describe. This is
  a translation of a changed library default, not a redesign.
- `V8` makes the new Modulith columns loadable instead of listing the problem as a rollout
  finding. Modulith 2.1.2 maps `completionAttempts` as a primitive `int`, so NULL values cannot
  load. Step 5 gives `completion_attempts` `NOT NULL DEFAULT 0`. The default also covers rows that
  Boot 3 instances insert without the column during a rolling deploy or rollback. Step 5 also
  backfills completed rows to `status = 'COMPLETED'`. Incomplete rows keep NULL `status`:
  Modulith 2.1.2 queries treat `status IS NULL AND completion_date IS NULL` as a legacy
  incomplete publication. Its failed-publication query matches `status = 'FAILED'` without a
  `completion_date` check. A `FAILED` backfill could therefore resubmit a row that a Boot 3
  instance completes during the rollout.

Resume state: the first Phase 1 run recorded the Boot 3 baseline at HEAD `919a2ee` as 253 tests,
0 failures, 0 errors, and 0 skipped. It applied steps 2–12, which remain uncommitted. `V8` already
contains the step 5 content below. On retry, do not rerun the baseline, because the worktree is no
longer unmodified. Use the recorded counts. Verify the existing changes against steps 2–12, apply
the `immediateWrites()` change in step 6, then continue from step 13.

Pre-plan evidence (2026-10-23, read-only): `currency-service` at HEAD `919a2ee`, with a clean
worktree.

- No copy was made and nothing was run: no Gradle, tests, or Docker. Unlike the earlier consumer
  plans, this plan has no dry run, no baseline test count, and no proof that the steps below pass.
  Phase 1 records the Boot 3 baseline first.
- Confirmed from cached 4.1.2 / Testcontainers 2.0.5 / ShedLock 7.10.1 jars and metadata:
  - The Boot 4 starters used below exist.
  - Moved classes:
    - `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`
    - `org.testcontainers.postgresql.PostgreSQLContainer` (non-generic)
  - Unchanged:
    - `org.springframework.boot.autoconfigure.task.TaskSchedulingProperties` (used by
      `SchedulingConfig`)
    - The ShedLock APIs `ShedLockConfig` uses
    - `OutputCaptureExtension`
    - The main-file `spring.jackson`, `spring.jpa`, `spring.flyway`, `spring.task`,
      `spring.ssl`, `spring.servlet`, and datasource keys
- The jars for these were not in the local cache, so they become open items for Phase 1:
  - Boot 4 data-redis, cache, amqp, and testcontainers modules
  - Spring Data Redis 4, Spring Modulith 2.1.2, Spring Cloud Stream (Oakwood)
  - Testcontainers `testcontainers-rabbitmq` 2.0.5
- No service-common defect is known that would block the suite, so there is no service-common fix
  phase. If Phase 1 finds one, it stops and reports. The fix then gets its own phase in a revised
  plan.

Behavior changes forced by the migration (listed only; nothing is built to detect them):

- Jackson 3 enables `FAIL_ON_NULL_FOR_PRIMITIVES`. Both `CurrencySeriesCreateRequest` and
  `CurrencySeriesUpdateRequest` declare `boolean enabled`. A request with `"enabled": null`, or
  without `enabled`, now returns 400.
  - On Boot 3 the field defaulted to `false`, so `POST /v1/currencies` without `enabled` changes
    from "created disabled" to 400.
  - Phase 1 aligns the documentation with this. The create request's `@Schema` says
    `NOT_REQUIRED` with `defaultValue = "true"`, which was already wrong on Boot 3, where the
    default was `false`. Phase 1 changes it to `REQUIRED` with no default, matching the update
    request. The generated OpenAPI then lists `enabled` as required for both requests.
  - The record types stay primitive. Existing tests always send `enabled`, and none assert the
    OpenAPI required list or default.
- Jackson 3 enables `FAIL_ON_TRAILING_TOKENS`. A JSON request body with content after the root
  value now returns 400.
- Jackson 3 sorts non-record properties alphabetically and reads and writes enums with
  `toString()`. The API types are records, and key order is not part of the contract. Newly
  written Redis entries, Modulith `serialized_event` payloads, and RabbitMQ message bodies may
  differ in key order and whitespace.
- Business-rule responses keep wire status 422. The Java constant is now
  `HttpStatus.UNPROCESSABLE_CONTENT`.
- service-common `0.0.18` always omits null `ApiErrorResponse`/`FieldError` fields. Production
  already used `default-property-inclusion: non_null`, so production responses do not change.
  The tests have no null-field assertions.
- `SafeLogger.toJson()` now logs `{}` for types without properties. The currency call sites log:
  - `List<ExchangeRateImportResult>` (records)
  - `FredSeriesObservationsResponse` (a record)
  - `CurrencyServiceProperties` (has properties)
  - `HttpLoggingProperties` (has properties)

  Their log output does not change.
- The FRED `WebClient` is built with `WebClient.builder()` and default codecs. Spring 7 uses
  Jackson 3 for those codecs. The FRED response records use boxed types and
  `@JsonIgnoreProperties(ignoreUnknown = true)`. The FRED WireMock tests cover this path.
- Modulith 2 writes and reads the `status`, `completion_attempts`, and `last_resubmission_date`
  columns that the new `V8` migration adds.
  - `V8` sets `completion_attempts` to 0 on existing rows and `status` to `COMPLETED` on existing
    completed rows. Existing incomplete rows keep NULL `status`.
  - `spring.modulith.events.delete-completion-after: 30d` has no effect, as on Boot 3. It is kept
    unchanged.

Main configuration: `src/test/resources/application.yml` fully shadows the main file. Tests
therefore do not exercise:

- The translated `spring.jackson.*` keys.
- `spring.jpa.hibernate.ddl-auto: validate`. Tests run with Boot's non-embedded default, `none`.
- `spring.cache.type: redis` and the `spring.cache.redis.*` keys. Tests set `none`, and the cache
  tests enable Redis per class.
- The Rabbit SSL settings.

So schema validation of `V8` against Modulith 2's entity, and the main-file translation, are
verified only at the deployed gate.

Rollout items. These are not tests and not part of Phase 1. They belong to the ecosystem rollout
after all services are migrated:

- Redis cache:
  - `exchangeRates` entries (`List<ExchangeRateData>`, under prefix `currency-service:`) are
    written by the Jackson 2 serializer with default typing.
  - The Jackson 3 serializer's type metadata may not read them, and no existing test round-trips
    that type.
  - Before rollout, choose how to handle old-format entries. Examples: flush the
    `currency-service:exchangeRates::*` keys at deploy, or change the key prefix (a config
    change, decided separately). Then confirm a round-trip in Tilt.
- Modulith in-flight events:
  - Incomplete rows written before `V8`, or by Boot 3 instances during the rollout, have NULL
    `status`, and Jackson 2 serialized their payloads. `V8` gives them
    `completion_attempts = 0`, so they load.
  - `republish-outstanding-events-on-restart: true` reloads incomplete rows at startup.
  - Before deploy, either drain incomplete publications (`completion_date IS NULL`) or confirm in
    Tilt that Modulith 2 deserializes and republishes such a row.
  - Decide `completion-mode` and retention explicitly. `delete-completion-after` is ignored.
- RabbitMQ: confirm that messages in flight on `exchangeRateImportRequested` during a rolling
  deploy are consumed by both versions.
- ShedLock: confirm that `@SchedulerLock` jobs still acquire and release against the existing `V2`
  `shedlock` table in Tilt.
- Schema validation: confirm that the main configuration (`ddl-auto: validate`) starts against
  `V1`–`V8` under Hibernate 7.
- Rollback: `V8` only adds columns. Boot 3 does not map them, and its inserts get the
  `completion_attempts` default, so a Boot 3 image should still pass `ddl-auto: validate`. Flyway's default `*:future` ignore pattern should let it start with `V8`
  applied. Verify both before relying on rollback.

Publication: nothing is pushed or published to GitHub until the whole ecosystem runs locally in
Tilt and api-tests pass. See the Snapshot Line in
[spring-boot-4-consumer-migration.md](spring-boot-4-consumer-migration.md#snapshot-line).

Deployed API gate: after all services are migrated, run currency-service with its main
configuration in Tilt and run `../budget-analyzer-api-tests`. Include the generated OpenAPI
contract, the request-body behavior changes above, and the rollout items. This later ecosystem
work is outside these two local phases. Until it passes, report: **Local migration verified;
deployed API compatibility pending.**

## Phase 1: Migrate Currency-Service To Boot 4

### Workspace

../currency-service

### Goal

Bring `currency-service` onto Spring Boot 4.1.2 and service-common `0.0.18-SNAPSHOT`.
`./gradlew clean build` must pass with the same test count as the Boot 3 baseline recorded in
step 1. Production changes stay mechanical: renamed dependencies, configuration keys, imports,
Jackson types, and one expand-only migration with its backfill. The only other changes are the
documentation update that marks `enabled` as required and the synchronous Redis cache writer.

### Scope

- `gradle/libs.versions.toml` and `build.gradle.kts`
- `src/main/resources/application.yml`
- New `src/main/resources/db/migration/V8__add_modulith_2_event_publication_columns.sql`
- Main sources: `config/CacheConfig.java`, `client/fred/FredClient.java`, and the `enabled`
  `@Schema` annotation in `api/request/CurrencySeriesCreateRequest.java`
- Test sources: `base/AbstractControllerTest.java`, `config/OpenApiContractIntegrationTest.java`,
  `config/TestContainersConfig.java`, and `fixture/FredApiStubs.java`
- `README.md` Technology Stack line (`Spring Boot 3.x`, `JUnit 5`)
- Review only, no change expected:
  - `.github/workflows/build.yml` and `Dockerfile`. Pre-plan review found JDK 25,
    `./gradlew build`, `**/build/test-results/test/*.xml`, and `build/libs/*.jar`. None of it is
    Boot-specific.
  - `docs/*.md`. Pre-plan review found no serializer class names, Jackson keys, or
    `event_publication` column lists.
  - `src/test/resources/application.yml`, which has no Jackson keys and no renamed keys known
    before the open items below are resolved.

### Non-goals

- New tests, snapshot or OpenAPI comparison files, a local `bootRun` check, a context-startup
  test, a Redis round-trip test, or a Modulith in-flight-event test.
- Removing deprecation warnings that do not block the build. Leave the 7
  `status().isUnprocessableEntity()` calls, deprecated `org.springframework.lang.NonNull` in
  `SchedulingConfig`, and the "Spring Boot 3.1+" Javadoc wording in `TestContainersConfig`.
- Changing `CurrencySeriesCreateRequest`/`CurrencySeriesUpdateRequest` to boxed types, changing
  their validation annotations, or any other change to the behavior-change list beyond the
  `@Schema` documentation in step 8.
- An undo script for `V8`. It is not required, and `V6` has none either.
- Dropping the redundant `spring-security-test` dependency, the unused `service-core` catalog
  entry, `delete-completion-after`, or any other cleanup, refactoring, or rollout work.
- Any change in service-common, orchestration, or api-tests.

### Required context

- `../service-common/docs/code-quality-standards.md`, before modifying Java.
- `../service-common/docs/testing-patterns.md`, before modifying tests.
- `docs/advanced-patterns-usage.md`, which this repository's AGENTS.md requires before changing
  caching, domain-event delivery, or messaging.
- `../service-common/docs/plans/spring-boot-4-transaction-service-migration.md` Phase 1 steps 2–6,
  as a worked example of the same catalog, build, and test relocations.
- `../service-common/docs/plans/spring-boot-4-consumer-migration.md`, sections "Findings From
  Consumer Migrations" and "Consumer integration inventory".
- `src/main/resources/db/migration/V5__add_event_publication_table.sql` and the files in Scope.

### Execution steps

1. Prerequisites and Boot 3 baseline:
   - Confirm that `0.0.18-SNAPSHOT` of `service-web`, `service-core`, `spring-platform`, and
     `spring-cloud-platform` is in Maven Local
     (`~/.m2/repository/org/budgetanalyzer/<artifact>/0.0.18-SNAPSHOT/`), and that Docker is
     available. If either is missing, stop and report. Do not publish service-common from this
     workspace.
   - On the unmodified worktree, run `./gradlew clean build`. Record the summed `tests`,
     `failures`, `errors`, and `skipped` from `build/test-results/test/*.xml`. If the baseline
     is not green, stop and report.
2. `gradle/libs.versions.toml`:
   - Set `springBoot = "4.1.2"` and `serviceCommon = "0.0.18-SNAPSHOT"`.
   - Delete the `junitPlatform` version and drop `version.ref = "junitPlatform"` from
     `junit-platform-launcher`.
   - Replace `spring-boot-starter-web` with
     `spring-boot-starter-webmvc = { module = "org.springframework.boot:spring-boot-starter-webmvc" }`.
   - Replace `spring-boot-starter-test` with `spring-boot-starter-webmvc-test`,
     `spring-boot-starter-data-jpa-test`, and `spring-boot-starter-security-test`, all in group
     `org.springframework.boot`. Keep `spring-security-test`.
   - Replace `flyway-core` with
     `spring-boot-starter-flyway = { module = "org.springframework.boot:spring-boot-starter-flyway" }`.
     Keep `flyway-database-postgresql`.
   - Change the `testcontainers-junit-jupiter`, `testcontainers-postgresql`, and
     `testcontainers-rabbitmq` modules to `org.testcontainers:testcontainers-junit-jupiter`,
     `org.testcontainers:testcontainers-postgresql`, and
     `org.testcontainers:testcontainers-rabbitmq`. The aliases stay the same.
   - Keep every other entry unchanged: `-webflux`, `-data-redis`, `-cache`, `-validation`,
     `-security`, `-actuator`, Cloud Stream, Modulith, ShedLock, SpringDoc, WireMock, Awaitility,
     and H2.
3. `build.gradle.kts`:
   - Change `implementation(libs.spring.boot.starter.web)` to `libs.spring.boot.starter.webmvc`.
   - Change `implementation(libs.flyway.core)` to `libs.spring.boot.starter.flyway`.
   - Replace `testImplementation(libs.spring.boot.starter.test)` with `testImplementation` of
     `libs.spring.boot.starter.webmvc.test`, `libs.spring.boot.starter.data.jpa.test`, and
     `libs.spring.boot.starter.security.test`.
   - Change nothing else. That includes the JaCoCo gates (LINE 0.90, BRANCH 0.85) and the
     repositories.
4. `src/main/resources/application.yml`:
   - Move `spring.jackson.serialization.write-dates-as-timestamps: false` to
     `spring.jackson.datatype.datetime.write-dates-as-timestamps: false`, keeping its inline
     comment.
   - Change `date-format` to `tools.jackson.databind.util.StdDateFormat`.
   - Keep `default-property-inclusion` and `serialization.indent-output` unchanged.
   - Rename any other key only if open item 4 shows it was renamed. Keep its value and comment.
     Change nothing else.
5. Add `src/main/resources/db/migration/V8__add_modulith_2_event_publication_columns.sql`:
   - Use one `ALTER TABLE event_publication` that adds `status VARCHAR(20)` (nullable),
     `completion_attempts INT NOT NULL DEFAULT 0`, and `last_resubmission_date TIMESTAMP`
     (nullable). `TIMESTAMP` matches the existing `V5` timestamp columns.
   - Follow it with
     `UPDATE event_publication SET status = 'COMPLETED' WHERE completion_date IS NOT NULL`.
     Leave `status` NULL on incomplete rows (see Resolved decisions).
   - Add `COMMENT ON COLUMN` lines in the style of `V5`, and a header comment explaining the
     default and the backfill.
   - Add no other defaults, constraints, backfill, or indexes. Do not modify `V5`.
   - If open item 5 shows that Modulith 2.1.2 maps a different column name or a length shorter
     than needed, use the mapped name or length and report it.
6. `config/CacheConfig.java`. Use open items 1–2 to pick the exact classes:
   - Replace the injected Jackson 2 `ObjectMapper` with the Boot `tools.jackson.databind.json.JsonMapper`
     bean.
   - Build the cache mapper from it with the same polymorphic setup: `NON_FINAL` default typing
     and the same validator allowlist.
     - Allowlist: `allowIfBaseType(Object.class)`, plus `allowIfSubType` for `org.budgetanalyzer`,
       `java.util`, and `java.time`.
     - The expected form is `jsonMapper.rebuild().activateDefaultTyping(typeValidator,
       DefaultTyping.NON_FINAL).build()`, with `tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator`
       and `tools.jackson.databind.DefaultTyping`.
   - Replace `GenericJackson2JsonRedisSerializer` with the Spring Data Redis 4 Jackson 3
     serializer. The expected class is `GenericJacksonJsonRedisSerializer`.
     - Use it for the `redisSerializer` bean type and for both bean-method parameters.
     - Construct it from that mapper, or through its builder with the same typing and validator.
       Keep the bean method names.
   - Move the `RedisCacheManagerBuilderCustomizer` import to its Boot 4 package.
   - Keep Boot 3's synchronous cache writes (see Resolved decisions):
     - Add a `RedisConnectionFactory redisConnectionFactory` parameter to
       `redisCacheManagerBuilderCustomizer`.
     - Call
       `builder.cacheWriter(RedisCacheWriter.create(redisConnectionFactory, configurer -> configurer.immediateWrites()))`
       (`org.springframework.data.redis.cache.RedisCacheWriter`).
     - Add a Javadoc bullet saying that writes, evictions, and clears complete before the call
       returns, overriding Spring Data Redis 4's asynchronous Lettuce default.
   - Keep everything else unchanged:
     - `transactionAware()`
     - `Duration.ZERO`
     - `StringRedisSerializer` keys
     - The `"currency-service:"` prefix
   - Update the Javadoc where it names the Jackson 2 serializer or `ObjectMapper`.
7. `client/fred/FredClient.java`:
   - Replace `com.fasterxml.jackson.databind.ObjectMapper` with
     `tools.jackson.databind.json.JsonMapper`. The field and constructor parameter become
     `jsonMapper`.
   - Replace `catch (JsonProcessingException e)` around `readValue(body, FredErrorResponse.class)`
     with `catch (JacksonException e)` (`tools.jackson.core.JacksonException`). The variable name
     and log line stay the same.
   - Keep the `com.fasterxml.jackson.annotation` imports on the FRED response records.
8. `api/request/CurrencySeriesCreateRequest.java`: in the `enabled` component's `@Schema`, change
   `requiredMode = Schema.RequiredMode.NOT_REQUIRED` to `Schema.RequiredMode.REQUIRED` and remove
   `defaultValue = "true"`. Keep the description, the example, and the `boolean` type.
9. `fixture/FredApiStubs.java`:
   - Replace `private static final ObjectMapper objectMapper = new ObjectMapper();` with
     `private static final JsonMapper jsonMapper = JsonMapper.builder().build();`
     (`tools.jackson.databind.json.JsonMapper`), and update both `writeValueAsString` calls.
   - Change both `catch (JsonProcessingException e)` to `catch (JacksonException e)`, and keep the
     `RuntimeException` messages.
   - Remove the Jackson 2 imports. The output stays the same: it serializes only `Map`, `List`,
     `String`, and `Integer` values, and neither mapper sorts map entries by default.
10. Test-source relocations:
   - `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` →
     `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, in
     `AbstractControllerTest` and `OpenApiContractIntegrationTest`.
   - `TestContainersConfig`:
     - `org.testcontainers.containers.PostgreSQLContainer<?>` / `new PostgreSQLContainer<>(...)`
       become the non-generic `org.testcontainers.postgresql.PostgreSQLContainer` /
       `new PostgreSQLContainer(...)`, in the static field and the bean method's return type.
     - `org.testcontainers.containers.RabbitMQContainer` becomes the class from open item 3.
       The expected class is `org.testcontainers.rabbitmq.RabbitMQContainer`.
     - Keep `GenericContainer<?>`, `DockerImageName`, the images, and the `@ServiceConnection`
       annotations, including `name = "redis"`, unless open item 3 shows a moved annotation
       package.
11. `README.md` Technology Stack: change `Spring Boot 3.x` to `Spring Boot 4.x` and `JUnit 5` to
   `JUnit 6`.
12. Confirm that `.github/workflows/build.yml`, `Dockerfile`, and `docs/*.md` reference nothing the
    steps above changed. Update a reference only if a change here invalidated it.
13. Run `./gradlew clean spotlessApply`, then `./gradlew clean build --warning-mode all`.
14. Fix mechanical migration failures in this repository, using the open items below.
    - If a failure traces to service-common behavior, stop and report the failing test, the
      Boot 3 behavior, and the actual result. Include a stack trace when relevant. Do not work
      around it here; the service-common fix needs its own phase.
    - Do the same if a failure needs a change outside Scope or the like-for-like rule, for
      example a redesigned setting. Report it instead of making it.

### Implementation notes

Open items. Confirm each after Gradle resolves the Boot 4 dependencies (step 13, or
`./gradlew dependencies`). Use `unzip -l` / `javap` on the resolved jars and the Boot
`META-INF/spring-configuration-metadata.json`. Record each answer in the final report.

1. Spring Data Redis 4 Jackson 3 serializer:
   - Find the exact class (expected `org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer`)
     and its constructors or builder.
   - Check whether it accepts a `JsonMapper` with default typing already active, or whether it
     configures typing itself. Either way, keep `NON_FINAL` typing and the same validator
     allowlist.
   - Find the Jackson 3 `PolymorphicTypeValidator` / `DefaultTyping` classes and the
     `activateDefaultTyping` signature.
   - Whether its stored format can read Jackson 2 entries is a rollout item. Do not test it
     here.
2. The Boot 4 package of `RedisCacheManagerBuilderCustomizer`. The expected package is
   `org.springframework.boot.cache.autoconfigure` in `spring-boot-cache`.
3. Testcontainers and service connections:
   - The `testcontainers-rabbitmq` 2.0.5 container class and whether it is still generic.
   - That `@ServiceConnection` (`org.springframework.boot.testcontainers.service.connection`)
     is unchanged.
   - That Boot 4 still provides service connection factories for PostgreSQL (confirmed:
     `spring-boot-jdbc`, `spring-boot-flyway`), RabbitMQ (`spring-boot-amqp`), and a
     `GenericContainer` named `redis` (`spring-boot-data-redis`).
   - If a factory's module is not on the test classpath, add only its Boot 4 test starter
     (`spring-boot-starter-amqp-test` or `spring-boot-starter-data-redis-test`) and report it.
4. Configuration keys:
   - Confirm in the Boot 4.1.2, Spring Cloud Stream, and Modulith 2.1.2 metadata that every main
     and test key still exists: `spring.data.redis.*` (including `ssl.bundle` and
     `lettuce.pool.*`), `spring.cache.type` and `spring.cache.redis.*`, `spring.rabbitmq.ssl.*`,
     `spring.cloud.function.definition`, `spring.cloud.stream.bindings.*` and
     `spring.cloud.stream.rabbit.bindings.*`, and `spring.modulith.events.*`.
   - Translate a renamed key like-for-like (step 4). Do not remove an unknown key; report it.
   - Check the function binding and Rabbit binder settings against the Spring Cloud 2025.1
     (Oakwood) release notes only if a messaging test fails.
5. Modulith 2.1.2 JPA publication entity:
   - Confirm the column names and types it maps for `status`, `completion_attempts`, and
     `last_resubmission_date`, and whether `completionAttempts` is a primitive.
   - The first run confirmed that `status` is `EnumType.STRING` (longest value 11 characters),
     `completion_attempts` is a primitive `int`, and `last_resubmission_date` is an `Instant`.
     Re-confirm that the step 5 `V8` matches these mappings.
   - Tests clear `event_publication` before running, so the default and backfill are verified
     only at the deployed gate.
6. Whether Flyway 12.4 still accepts the existing `U*__rollback_*.sql` files in
   `db/migration`. If it rejects them, stop and report; do not delete or move them.
7. Whether the JaCoCo gates (LINE 0.90, BRANCH 0.85) still pass. If coverage drops because of the
   migration, report it. Do not lower the gates or add tests.

Other notes:

- Let `spotlessApply` order the `tools.jackson` imports and wrap long lines.
- Every Boot 4 starter, Testcontainers 2 module, the JUnit launcher, Spring Cloud, Modulith, and
  ShedLock are version-managed through `spring-cloud-platform`. Add no versions.
- Boot 4 registers only a Jackson 3 `JsonMapper` bean. Jackson 2 still arrives through SpringDoc,
  so a stray `com.fasterxml` `ObjectMapper` injection compiles but fails at startup. The
  validation `rg` catches this.
- `spring-boot-starter-webflux` 4.1.2 no longer brings a `WebClient.Builder` bean. That is fine,
  because `WebClientConfig` defines its own builder. Keep the webflux starter.
- These need no change:
  - `SchedulingConfig`, `ShedLockConfig`, and `CurrencyServiceStartupConfig`.
  - The messaging records, `StreamBridge`, and `@ApplicationModuleListener` consumers.
  - The entities (`AuditableEntity`, `jakarta.persistence`) and repositories.
  - `MessageConsumerIntegrationTest` (`OutputCaptureExtension`).
  - `TestDatabaseHelper`, which only uses the `event_publication` and `shedlock` columns that
    already exist.

### Validation

- Step 1 records a green Boot 3 baseline and its test count.
- `./gradlew clean spotlessApply` succeeds.
- `./gradlew clean build --warning-mode all` succeeds.
  - The summed `tests` count across `build/test-results/test/*.xml` equals the baseline, with 0
    failures and errors. The `skipped` count equals the baseline.
  - The JaCoCo, Checkstyle, and Spotless gates pass.
- This command returns no matches:
  `rg -n 'com\.fasterxml\.jackson\.(core|databind|datatype)|GenericJackson2JsonRedisSerializer|org\.springframework\.boot\.autoconfigure\.cache|org\.testcontainers\.containers\.(PostgreSQLContainer|RabbitMQContainer)|org\.springframework\.boot\.test\.autoconfigure\.(orm|jdbc|web)|junitPlatform|starter-web"|flyway-core|"org\.testcontainers:(junit-jupiter|postgresql|rabbitmq)"|value\(nullValue\(\)\)' src gradle build.gradle.kts`
- `rg -n 'status|completion_attempts|last_resubmission_date' src/main/resources/db/migration/V8__*.sql`
  shows all three columns, the `NOT NULL DEFAULT 0`, and the `COMPLETED` backfill.
- `rg -n 'immediateWrites' src/main/java/org/budgetanalyzer/currency/config/CacheConfig.java`
  shows the synchronous cache writer.
- All four cache-eviction tests in `ExchangeRateImportServiceIntegrationTest` pass with their
  assertions unchanged.
- `git status --short` lists only files in Scope, plus a test starter only if open item 3
  required it.

### Completion criteria

- The worktree has the Scope changes uncommitted, and nothing else.
- The full build passes with the baseline test count. Existing assertions are unchanged.
- The final report includes:
  - The baseline and migrated test counts.
  - The answer to each open item.
  - The deprecation notes left in place.
  - The behavior-change list and rollout items from the preamble, with any additions from the
    open items.

  It states: "Local migration verified; deployed API compatibility pending."

## Phase 2: Record Currency-Service Migration Status In Service-Common Docs

### Workspace

.

### Goal

Update the service-common Boot 4 consumer notes to show that currency-service builds and passes
its suite against `0.0.18-SNAPSHOT`. Keep the deployed API gate and the rollout items marked as
outstanding.

### Scope

- `docs/plans/spring-boot-4-consumer-migration.md`

### Non-goals

- Editing canonical (version-agnostic) docs, `AGENTS.md`, `README.md`, or any source or build file.
- Recording anything for other consumers.
- Rewriting the consumer integration inventory beyond correcting a currency-service fact that
  Phase 1 disproved.

### Required context

- The file in Scope (`## Status`, and the currency-service bullets under "Consumer integration
  inventory").
- Phase 1 results, read-only:
  - `git -C ../currency-service status --short`
  - `git -C ../currency-service log -1 --format=%h`
  - The test count from `../currency-service/build/test-results/test/*.xml`
  - The Phase 1 final report, including the open-item answers

### Execution steps

1. In `## Status`, after the transaction-service paragraph, add a short paragraph:
   - `currency-service` is migrated locally, uncommitted on top of the recorded HEAD
     ([currency-service plan](spring-boot-4-currency-service-migration.md)).
   - Against the Maven Local snapshot, it builds and passes all its existing tests (state the
     count). It includes an expand-only `V8` migration for the Modulith 2 `event_publication`
     columns, with a `completion_attempts` default and a `COMPLETED` backfill, and keeps
     synchronous Redis cache writes through `immediateWrites()`.
   - Nothing has been deployed. State: "Local migration verified; deployed API compatibility
     pending."
   - Still outstanding, at the deployed gate and rollout:
     - The main-configuration keys, which tests shadow
     - `ddl-auto: validate` against `V8`
     - Redis old-format cache entries
     - Modulith in-flight publications
     - ShedLock lock acquisition
     - The primitive `enabled` request-field behavior change (now documented as required)
2. If Phase 1 confirmed class names that the inventory marks as "verify", such as the Spring Data
   Redis Jackson 3 serializer, add them as a short clause in the existing currency-service
   bullets. Keep the wording concise and consistent with the surrounding sections.

### Implementation notes

- This is a release-specific note under `docs/plans/`, so version-specific detail belongs here.
- Read only `../currency-service`. Do not read orchestration or api-tests.

### Validation

- `git status --short` shows only the Scope file, plus this plan if it is still uncommitted.
- Every changed sentence is consistent with the Phase 1 results.

### Completion criteria

- The consumer notes separate the verified local currency-service result from the pending
  deployed API compatibility and the listed rollout items.
