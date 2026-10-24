# Spring Boot 4 Permission-Service Research

Notes for migrating `permission-service` (HEAD `84a1516`, the last Boot 3 state; service-common
`0.0.17`, Boot 3.5.16) to Boot 4.1.2 and service-common `0.0.18-SNAPSHOT`. This is not a plan.
General consumer caveats are in [spring-boot-4-consumer-migration.md](spring-boot-4-consumer-migration.md).

The migration should be the minimal changeset that compiles, starts, and passes the existing test
suite. The external contract is the same endpoints, parameters, status codes, fields, and values;
JSON key order and OpenAPI formatting are not part of it. Existing tests are reused; focused
assertions or cases are added only where needed to show API compatibility, and must pass on Boot 3
before the dependency change. `../budget-analyzer-api-tests` verifies the contract after
deployment.

Status: migrated locally, uncommitted on top of `84a1516`. The build passes 57 tests (56 existing
plus one new invalid-`status` validation case). Focused assertions for timestamps, null omission,
detail and pagination fields, and 400/401/403/404/422/503 error shapes passed on Boot 3 and
unchanged on Boot 4. **Local migration verified; deployed API compatibility pending.**

## Configuration

- `src/test/resources/application.yml` shadows `src/main/resources/application.yml` in tests, so
  apply each config translation to both files.
- `spring.jackson.serialization.write-dates-as-timestamps: false` (both files) moves to
  `spring.jackson.datatype.datetime.write-dates-as-timestamps: false`. In Jackson 3 it is a
  `DateTimeFeature`, not a `SerializationFeature`. With the translated key in the test file,
  timestamp assertions (including microsecond and millisecond values) pass unchanged on Boot 4.
- `spring.jackson.date-format: com.fasterxml.jackson.databind.util.StdDateFormat` (main file only)
  changes to `tools.jackson.databind.util.StdDateFormat`. Tests do not load the main file, so its
  Jackson settings remain pending verification at the deployed gate (Tilt plus
  `../budget-analyzer-api-tests`).
- `default-property-inclusion` and `serialization.indent-output` are unchanged.
- Redis is unused: `spring-boot-starter-data-redis` is in the catalog but not in
  `build.gradle.kts`, and the `spring.data.redis` properties are dead. Leave them as they are.

## Build Changes

- Update `serviceCommon` to `0.0.18-SNAPSHOT` and `springBoot` to `4.1.2` together.
- `spring-boot-starter-web` becomes `spring-boot-starter-webmvc`.
- `spring-boot-starter-test` becomes `spring-boot-starter-webmvc-test`,
  `spring-boot-starter-data-jpa-test`, and `spring-boot-starter-security-test`.
- `flyway-core` becomes `spring-boot-starter-flyway`, because Boot 4's Flyway autoconfiguration
  lives in `spring-boot-flyway`. Observed: the starter is required; with only `flyway-core` the
  `Flyway` bean is missing and the full-context integration tests fail. Keep
  `flyway-database-postgresql`.
- Rename `org.testcontainers:junit-jupiter` and `org.testcontainers:postgresql` to
  `testcontainers-junit-jupiter` and `testcontainers-postgresql`.
- Remove the `junitPlatform = "1.14.4"` pin.
- Flyway 12's breaking changes (MongoDB, SQLFluff, extension APIs) do not affect this service.
  Hibernate 7 with `ddl-auto: validate` is exercised by the existing repository tests.

## Source Changes

All mechanical:

- `UserDetailsServiceAutoConfiguration` (`PermissionServiceApplication`) moves to
  `org.springframework.boot.security.autoconfigure`.
- Test annotation relocations:
  - `DataJpaTest` moves to `org.springframework.boot.data.jpa.test.autoconfigure`.
  - `TestEntityManager` moves to `org.springframework.boot.jpa.test.autoconfigure`.
  - `AutoConfigureTestDatabase` moves to `org.springframework.boot.jdbc.test.autoconfigure`.
  - `AutoConfigureMockMvc` moves to `org.springframework.boot.webmvc.test.autoconfigure`.
- `org.testcontainers.containers.PostgreSQLContainer<?>` becomes
  `org.testcontainers.postgresql.PostgreSQLContainer` in four test files:
  `PermissionServiceIntegrationTestSupport`, `UserRoleRepositoryIntegrationTest`,
  `SeedDataIntegrationTest`, and `UserIdentityIntegrationTest`.
- `PermissionOpenApiIntegrationTest` changes Jackson 2 `ObjectMapper`/`JsonNode` to
  `tools.jackson.databind.json.JsonMapper`/`JsonNode`, and `asText()` to `asString()`.
- `InternalPermissionControllerIntegrationTest` changes one `isUnprocessableEntity()` to
  `isUnprocessableContent()`.
- No changes needed:
  - `PermissionServiceSecurityConfig` already uses a lower `@Order` with a `securityMatcher`, which
    the 0.0.18 shared chain requires.
  - `SessionGatewayClient` builds its own `RestClient`, so Boot 4's RestClient autoconfiguration
    move does not apply.
  - Enums do not override `toString()`, so Jackson 3's enum-to-string default leaves values
    unchanged.

## Possible Contract Differences To Call Out

- SpringDoc 3 was expected to possibly deduplicate the `status` parameter enum
  `["ACTIVE","DEACTIVATED","ACTIVE","DEACTIVATED"]`. In the pre-plan dry run it did not: the
  enum stayed duplicated and the generated `/v3/api-docs` was byte-identical to Boot 3. The
  deployed gate still compares the OpenAPI contract against Boot 3.
- The unified OpenAPI doc takes the shared `ApiErrorResponse`, `FieldError`, and
  `PageMetadataResponse` schemas from permission-service, because it is merged last. Any SpringDoc
  3 change to those schemas surfaces through this service.

## CI

- `.github/workflows/*.yml` and the `Dockerfile` need no Boot 4 change: JDK 25, `./gradlew build`,
  the `build/test-results/test/*.xml` upload path, and the `bootJar` output copy are unchanged.
  Publication policy is in
  [spring-boot-4-consumer-migration.md](spring-boot-4-consumer-migration.md#snapshot-line).
