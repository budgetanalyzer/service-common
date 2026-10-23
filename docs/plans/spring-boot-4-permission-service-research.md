# Spring Boot 4 Permission-Service Research

Notes for migrating `permission-service` (HEAD `84a1516`, the last Boot 3 state; service-common
`0.0.17`, Boot 3.5.16) to Boot 4.1.2 and service-common `0.0.18-SNAPSHOT`. This is not a plan.
General consumer caveats are in [spring-boot-4-consumer-migration.md](spring-boot-4-consumer-migration.md).

The migration should be the minimal changeset that compiles, starts, and passes the existing test
suite. The external contract is the same endpoints, parameters, status codes, fields, and values;
JSON key order and OpenAPI formatting are not part of it. Do not add tests;
`../budget-analyzer-api-tests` verifies the contract after deployment.

Items marked *unverified* are inferred and should be confirmed during the migration.

## Configuration

- `src/test/resources/application.yml` shadows `src/main/resources/application.yml` in tests, so
  apply each config translation to both files.
- `spring.jackson.serialization.write-dates-as-timestamps: false` (both files) moves to
  `spring.jackson.datatype.datetime.write-dates-as-timestamps: false`. In Jackson 3 it is a
  `DateTimeFeature`, not a `SerializationFeature`. Whether the stale key fails startup or is
  ignored is *unverified*.
- `spring.jackson.date-format: com.fasterxml.jackson.databind.util.StdDateFormat` (main file only)
  changes to `tools.jackson.databind.util.StdDateFormat`.
- `default-property-inclusion` and `serialization.indent-output` are unchanged.
- Redis is unused: `spring-boot-starter-data-redis` is in the catalog but not in
  `build.gradle.kts`, and the `spring.data.redis` properties are dead. Leave them as they are.

## Build Changes

- Update `serviceCommon` to `0.0.18-SNAPSHOT` and `springBoot` to `4.1.2` together.
- `spring-boot-starter-web` becomes `spring-boot-starter-webmvc`.
- `spring-boot-starter-test` becomes `spring-boot-starter-webmvc-test`,
  `spring-boot-starter-data-jpa-test`, and `spring-boot-starter-security-test`.
- `flyway-core` becomes `spring-boot-starter-flyway`, because Boot 4's Flyway autoconfiguration
  lives in `spring-boot-flyway`. *Unverified* for this service; the integration tests inject the
  `Flyway` bean, so a missing autoconfiguration fails loudly. Keep `flyway-database-postgresql`.
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

- SpringDoc 3 may deduplicate the `status` parameter enum, which is currently
  `["ACTIVE","DEACTIVATED","ACTIVE","DEACTIVATED"]`. The api-tests drift check would report it.
  *Unverified*.
- The unified OpenAPI doc takes the shared `ApiErrorResponse`, `FieldError`, and
  `PageMetadataResponse` schemas from permission-service, because it is merged last. Any SpringDoc
  3 change to those schemas surfaces through this service.

## Outside The Code Change

- CI resolves service-common from GitHub Packages, where `0.0.18-SNAPSHOT` is not published. It
  fails once the pin moves, until the release gate allows remote publication.
