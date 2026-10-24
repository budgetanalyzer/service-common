# Spring Boot 4 Session-Gateway Migration Plan

Migrate `session-gateway` from Spring Boot 3.5.16 / service-common `0.0.17` to Spring Boot 4.1.2 /
service-common `0.0.18-SNAPSHOT`. Make only the changes required to compile, start, and pass the
existing session-gateway test suite. General consumer caveats are in
[spring-boot-4-consumer-migration.md](spring-boot-4-consumer-migration.md). Use its "Findings From
Consumer Migrations" as established facts. The
[permission-service](spring-boot-4-permission-service-migration.md) and
[currency-service](spring-boot-4-currency-service-migration.md) plans are worked examples of the
same build and test relocations. Where those documents differ, this plan governs scope.
session-gateway is the only WebFlux and OAuth2-client consumer, so its stack-specific steps have
no worked example.

Plan-wide constraints:

- Contract means the same endpoints, parameters, status codes, response fields, and values. JSON
  key order, whitespace, and OpenAPI formatting are not part of the contract.
- Do not add tests, snapshots, smoke checks, or any verification harness. The existing tests are
  the local verification. `../budget-analyzer-api-tests` is the verification after all services
  are migrated and deployed. You may augment an existing test to cover fragile behavior. This plan
  adds no such assertion: the error-body null checks already use `jsonPath(...).doesNotExist()`,
  which passes whether a field is null or absent. Never modify, weaken, or delete existing
  assertions to make changes pass.
- Translate configuration like-for-like (renamed keys, modules, and classes only). Do not remove or
  redesign settings.
- If the migration forces a behavior change, list it here. Do not build anything to detect or
  work around it.
- Do not read orchestration or api-tests internals. The one exception is
  `../orchestration/docs/agents-md-checkstyle.md`, which Phase 1 reads before editing
  `AGENTS.md`.
- Keep all documentation up to date in the same phase as the change it describes. Do not leave
  documentation updates as follow-up work.
- No git write operations. Leave all changes uncommitted.

Pre-plan evidence (2026-10-24, read-only): `session-gateway` at HEAD `09dacbf`, with a clean
worktree. HEAD reverts `ed0b45f`, which changed only the `serviceCommon` version in
`gradle/libs.versions.toml`.

- No copy was made and nothing was run: no Gradle, tests, or Docker. This plan has no dry run, no
  baseline test count, and no proof that the steps below pass. The checked-in
  `build/test-results` files are stale and are not a baseline. Phase 1 records the Boot 3 baseline
  first.
- Confirmed from cached Boot 4.1.2, Spring Framework 7.0.10, Spring Security 7.1.2 (config, web,
  core), Spring Data Redis 4.1.2, Testcontainers 2.0.5, and SpringDoc 3.1.1 jars and metadata:
  - The 4.1.2 BOM manages `spring-boot-starter-webflux-test` and
    `spring-boot-starter-security-oauth2-client`. It also still lists
    `spring-boot-starter-oauth2-client`.
  - `spring-boot-starter-webflux` 4.1.2 brings `spring-boot-starter-jackson`, so Boot's
    `JsonMapper` bean is available. `spring-boot-starter-webflux-test` brings
    `spring-boot-webtestclient`, `spring-boot-starter-test`, and `reactor-test`.
  - Moved classes:
    - `org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient`
    - `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`
      (`spring-boot-jdbc`)
    - `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration`
      (`spring-boot-hibernate`)
  - Unchanged: `SpringBootTest`, `TestConfiguration`, `ApplicationContextRunner`,
    `ServiceConnection`, `YamlPropertySourceLoader`, `ConfigurationPropertiesBindException`,
    `BindException`, `ApplicationReadyEvent`, `EnableConfigurationProperties`,
    `org.testcontainers.containers.GenericContainer` (still generic),
    `org.testcontainers.junit.jupiter.Testcontainers`, and `DockerImageName`.
  - The Redis service-connection factory for a `GenericContainer` named `redis` ships in the
    `spring-boot-data-redis` main jar. No Redis test starter is needed.
  - Every non-OAuth2 `org.springframework.security`, `web`, `http`, `data`, `context`, and `core`
    import in `src` exists in the 7.x jars.
  - The service-common `0.0.18-SNAPSHOT` POMs bring no JDBC or JPA modules. session-gateway's own
    starters do not bring them either. So the relocated exclude classes are expected to be absent
    from the classpath, and class literals for them would not compile. In
    `AutoConfigurationImportSelector.checkExcludedClasses` (Boot 4.1.2, javap), an exclude is
    invalid only if the class is present and is not an auto-configuration. An `excludeName` for an
    absent class is therefore accepted.
  - Main-config keys that still exist: `server.port`, `server.address`,
    `server.forward-headers-strategy`, `server.error.whitelabel.enabled`, `spring.ssl.bundle.pem`,
    `spring.jackson.default-property-inclusion`, `spring.data.redis.{host, port, username,
    password, timeout, ssl.enabled, ssl.bundle}`, `management.endpoint.health.{show-details,
    probes.enabled}`, and `springdoc.{api-docs, swagger-ui}.enabled`.
  - Renamed key: `spring.codec.max-in-memory-size` (in `application-kubernetes.yml`) no longer
    exists. Boot 4 binds `spring.http.codecs.max-in-memory-size` (`HttpCodecsProperties` in
    `spring-boot-http-codec`).
  - `testcontainers.properties`: Testcontainers 2.0.5 still reads `docker.client.strategy`, and
    all three strategy classes it names still exist. No change.
  - service-common's `ReactiveErrorWebExceptionHandler` keeps order `-1`. session-gateway's
    handlers are plain `WebExceptionHandler`s or `@ControllerAdvice` (orders `-2`,
    `HIGHEST_PRECEDENCE`, `HIGHEST_PRECEDENCE + 1`). None implements Boot's relocated
    `ErrorWebExceptionHandler`.
- Documentation that the migration affects:
  - `AGENTS.md` "Writing tests" names "JUnit 5 + TestContainers". Boot 4.1.2 manages JUnit 6.
  - `AGENTS.md` "Service-Common Integration" says `OAuth2ResourceServerSecurityConfig` is
    "explicitly excluded". No class with that name exists in either repository. This is stale from
    before the migration, but it sits in the bullets this migration edits.
  - The `SessionGatewayApplication` comment says DataSource/JPA auto-configuration is "brought in
    by service-common-core". It is not, as of `0.0.18`.
  - `README.md` and `docs/*.md` contain no Boot, JUnit, Jackson 2, starter, or codec references
    that the migration invalidates.
- These jars were not in the local cache, so they become Phase 1 open items:
  - `spring-boot-starter-security-oauth2-client` 4.1.2 and `spring-boot-security-oauth2-client`
    4.1.2 (and their configuration metadata)
  - `spring-security-oauth2-client`, `-oauth2-core`, and `-oauth2-jose` 7.1.2
- No service-common defect is known that would block the suite, so there is no service-common fix
  phase. If Phase 1 finds one, it stops and reports. The fix then gets its own phase in a revised
  plan.

Behavior changes forced by the migration (listed only; nothing is built to detect them):

- No request-body change. The endpoints are `GET /logout`, `GET /auth/v1/user`,
  `GET /auth/v1/session`, and `DELETE /internal/v1/sessions/users/{userId}`. None reads a JSON
  body, so Jackson 3's `FAIL_ON_TRAILING_TOKENS` and `FAIL_ON_NULL_FOR_PRIMITIVES` do not apply to
  inbound requests.
- `SecurityConfig` writes its 401/503 `ApiErrorResponse` bodies with Boot's Jackson 3 `JsonMapper`
  instead of the Jackson 2 `ObjectMapper`. service-common `0.0.18` always omits null
  `ApiErrorResponse` fields. Production already used `default-property-inclusion: non_null`, so
  production bodies do not change. In tests, null fields that used to be written are now omitted.
  The existing `doesNotExist()` assertions pass either way.
- Outbound responses are decoded by Spring 7's default Jackson 3 WebClient codecs. That covers
  permission-service and the IdP calls that use those codecs.
  - Unknown properties are still ignored (`FAIL_ON_UNKNOWN_PROPERTIES` is false in Jackson 3.1.7).
  - A response with content after the root JSON value now fails to decode.
  - The permission-service response records use no primitives, and `AccountStatus` has no
    `toString()` override. Their decoding is unchanged.
- Response records (`UserInfoResponse`, `SessionStatusResponse`) keep their fields and values.
  Jackson 3 key order is not part of the contract.
- The `DataSourceAutoConfiguration` / `HibernateJpaAutoConfiguration` excludes have no effect when
  those classes are absent, which is expected (open item 5). Nothing changes at runtime: no
  DataSource or JPA auto-configuration runs either way.
- SpringDoc 3.1.1 generates `/v3/api-docs` when `SPRINGDOC_ENABLED` is set (default `false`).
  Formatting is not part of the contract.
- Redis storage does not change. `SessionWriter`/`SessionReader` and
  `RedisAuthorizationRequestRepository` store flat string hash fields through
  `ReactiveStringRedisTemplate` and the Lua scripts in `src/main/resources/redis/`. No JSON is
  persisted, so Boot 3 and Boot 4 pods read each other's sessions and OAuth2 state. This answers
  the consumer inventory's rolling-deployment question, and there is no Redis rollout item.
- Any Spring Security 7 default change that alters the OAuth2 login flow is not known yet (open
  item 4). If Phase 1 finds one, it is added here.

Main configuration: `src/test/resources/application.yml` fully shadows the main file, and no test
activates the `kubernetes` profile. Tests therefore do not exercise:

- `spring.jackson.default-property-inclusion: non_null`
- `server.forward-headers-strategy`, `server.error.whitelabel.enabled`, and `server.address`
- `spring.ssl.bundle.pem.infra-ca` and `spring.data.redis.ssl.*`
- `management.endpoint.health.*` and `springdoc.*`
- The renamed `spring.http.codecs.max-in-memory-size` in `application-kubernetes.yml`

These are verified only at the deployed gate.

Publication: nothing is pushed or published to GitHub until the whole ecosystem runs locally in
Tilt and api-tests pass. See the Snapshot Line in
[spring-boot-4-consumer-migration.md](spring-boot-4-consumer-migration.md#snapshot-line). The
`Dockerfile` resolves service-common from GitHub Packages and cannot resolve the Maven Local
snapshot. That is a publication-order concern, not a Phase 1 change.

Deployed API gate: after all services are migrated, run session-gateway with its main and
`kubernetes` configuration in Tilt and run `../budget-analyzer-api-tests`. Include the OAuth2
login, callback, logout, and session endpoints, the 401/503 error bodies, and the main-config keys
above. This later ecosystem work is outside these two local phases. Until it passes, report:
**Local migration verified; deployed API compatibility pending.**

## Phase 1: Migrate Session-Gateway To Boot 4

### Workspace

../session-gateway

### Goal

Bring `session-gateway` onto Spring Boot 4.1.2 and service-common `0.0.18-SNAPSHOT`.
`./gradlew clean build` must pass with the same test count as the Boot 3 baseline recorded in
step 1. All code and configuration changes stay mechanical: renamed dependencies, one renamed
configuration key, relocated imports, the `excludeName` translation, and the Jackson type in
`SecurityConfig`. Documentation, including `AGENTS.md` and source comments, matches the migrated
state when the phase ends.

### Scope

- `gradle/libs.versions.toml` and `build.gradle.kts`
- `src/main/java/org/budgetanalyzer/sessiongateway/SessionGatewayApplication.java`
- `src/main/java/org/budgetanalyzer/sessiongateway/config/SecurityConfig.java`
- `src/main/resources/application-kubernetes.yml`
- `src/test/java/org/budgetanalyzer/sessiongateway/base/AbstractIntegrationTest.java`
- `AGENTS.md`: the "Writing tests" line under "Spring Boot Patterns" and the
  "Service-Common Integration" bullets under "Technology Stack" (step 8)
- Other main or test sources only where open item 2 shows a removed or renamed Spring Security 7
  OAuth2 API with a like-for-like replacement
- Any other documentation that a change in this phase makes inaccurate (step 9)
- Review only, no change expected:
  - `.github/workflows/build.yml`, `dependency-submission.yml`, `publish-release.yml`, and
    `Dockerfile`. Pre-plan review found JDK 25, `./gradlew build`, `bootJar`, and
    `**/build/test-results/test/*.xml`. None of it is Boot-specific.
  - `README.md` and `docs/*.md`. The README Tech Stack names no versions.
    `docs/configuration.md` documents `spring.jackson.default-property-inclusion: non_null`,
    which still exists. `docs/dependency-automation.md` names libraries without versions.
  - `src/main/resources/application.yml`, `src/test/resources/application.yml`, and
    `src/test/resources/testcontainers.properties`. Their keys are confirmed above, except the
    OAuth2 client keys (open item 3).

### Non-goals

- New tests, snapshot or OpenAPI comparison files, a local `bootRun` check, a context-startup
  test, or an OAuth2 flow test.
- Removing deprecation warnings that do not block the build.
- Removing the DataSource/JPA excludes. Step 4 corrects their comment only.
- Restructuring or tightening `AGENTS.md` beyond the lines step 8 names. Other unrelated
  documentation cleanup is also out of scope.
- Removing the explicit `OAuth2AuthorizationRequestCustomizers.withPkce()` or other OAuth2
  configuration, even if Security 7 makes it a default.
- Adding `spring-boot-starter-security-test`, `-data-redis-test`, or
  `-security-oauth2-client-test`. The suite imports nothing from `org.springframework.security.test`
  or the Redis/OAuth2 test slices, and Boot 3's `spring-boot-starter-test` included none of them.
- Any change in service-common, orchestration, or api-tests.

### Required context

- `../service-common/docs/code-quality-standards.md`, before modifying Java.
- `../service-common/docs/testing-patterns.md`, before modifying tests.
- `../orchestration/docs/agents-md-checkstyle.md`, before editing `AGENTS.md` (this repository's
  AGENTS.md requires it). Read nothing else in orchestration.
- `../service-common/service-web/src/main/java/org/budgetanalyzer/service/security/ReactiveClaimsHeaderSecurityConfig.java`,
  to confirm the back-off behavior that step 8 documents.
- `../service-common/docs/plans/spring-boot-4-currency-service-migration.md` Phase 1 steps 1–3 and
  13–14, as a worked example of the baseline, catalog, and build steps.
- `../service-common/docs/plans/spring-boot-4-consumer-migration.md`, sections "Findings From
  Consumer Migrations" and "Consumer integration inventory".
- The files in Scope, plus `config/OAuth2ClientConfig.java`,
  `config/IdpReactiveJwtDecoderFactory.java`, and `security/RedisAuthorizationRequestRepository.java`
  for open item 2.

### Execution steps

1. Prerequisites and Boot 3 baseline:
   - Confirm that `0.0.18-SNAPSHOT` of `service-web`, `service-core`, and `spring-platform` is in
     Maven Local (`~/.m2/repository/org/budgetanalyzer/<artifact>/0.0.18-SNAPSHOT/`), and that
     Docker is available. If either is missing, stop and report. Do not publish service-common
     from this workspace.
   - On the unmodified worktree, run `./gradlew clean build`. Record the summed `tests`,
     `failures`, `errors`, and `skipped` from `build/test-results/test/*.xml`. If the baseline is
     not green, stop and report.
2. `gradle/libs.versions.toml`:
   - Set `springBoot = "4.1.2"` and `serviceCommon = "0.0.18-SNAPSHOT"`.
   - Delete the `junitPlatform` version and drop `version.ref = "junitPlatform"` from
     `junit-platform-launcher`.
   - Replace `spring-boot-starter-oauth2-client` with
     `spring-boot-starter-security-oauth2-client = { module = "org.springframework.boot:spring-boot-starter-security-oauth2-client" }`
     (open item 1).
   - Replace `spring-boot-starter-test` with
     `spring-boot-starter-webflux-test = { module = "org.springframework.boot:spring-boot-starter-webflux-test" }`.
   - Change the `testcontainers-junit-jupiter` module to
     `org.testcontainers:testcontainers-junit-jupiter`. The alias stays the same.
   - Keep every other entry unchanged: `-webflux`, `-data-redis`, `-actuator`,
     `spring-boot-testcontainers`, `testcontainers`, SpringDoc, WireMock, Awaitility,
     `reactor-test`, and the plugin and tool versions.
3. `build.gradle.kts`:
   - Change `implementation(libs.spring.boot.starter.oauth2.client)` to
     `libs.spring.boot.starter.security.oauth2.client`.
   - Change `testImplementation(libs.spring.boot.starter.test)` to
     `libs.spring.boot.starter.webflux.test`.
   - Change nothing else. That includes the JaCoCo gates (LINE 0.90, BRANCH 0.65), the
     repositories, the Checkstyle configuration, and the test JVM arguments.
4. `SessionGatewayApplication.java`:
   - Replace `exclude = { DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class }`
     with
     `excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration", "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"}`.
   - Keep the first comment line (`// Session Gateway uses Redis only, not a database`).
   - Replace the second comment line, which becomes inaccurate because service-common `0.0.18` no
     longer brings JDBC or JPA, with
     `// Exclude DataSource and JPA auto-configuration if a dependency brings them onto the classpath`.
     If open item 5 shows that both modules are on the runtime classpath, name the dependency
     that brings them instead.
   - Remove the two old imports.
5. `config/SecurityConfig.java`:
   - Replace `com.fasterxml.jackson.databind.ObjectMapper` with
     `tools.jackson.databind.json.JsonMapper`. The field and constructor parameter become
     `jsonMapper`, and the `Mono.fromCallable(() -> jsonMapper.writeValueAsBytes(apiErrorResponse))`
     call keeps its shape. Jackson 3's `JacksonException` is unchecked, and `fromCallable` still
     propagates it as an error signal.
   - Update any Javadoc or comment that names `ObjectMapper`. Change nothing else in the security
     DSL.
6. `src/main/resources/application-kubernetes.yml`: move `spring.codec.max-in-memory-size: 10MB`
   to `spring.http.codecs.max-in-memory-size: 10MB` (`spring:` → `http:` → `codecs:` →
   `max-in-memory-size`). Keep the `# WebFlux codec configuration for file uploads` comment above
   it. Change nothing else.
7. `base/AbstractIntegrationTest.java`: change the import
   `org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient` to
   `org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient`. Keep the
   annotations and `@DynamicPropertySource` unchanged.
8. `AGENTS.md`: read `../orchestration/docs/agents-md-checkstyle.md` first, and follow its
   Preservation Rule and AG005 (no dependency versions). Edit only these lines:
   - "Spring Boot Patterns" → "Writing tests": change `JUnit 5 + TestContainers conventions` to
     `JUnit + Testcontainers conventions`. Drop the major version instead of changing it to 6,
     because version numbers belong in `gradle/libs.versions.toml`.
   - "Technology Stack" → "Service-Common Integration":
     - Replace `OAuth2ResourceServerSecurityConfig explicitly excluded (Session Gateway is OAuth2
       Client, not Resource Server)`. Pre-plan review found that no class with that name exists
       in session-gateway or service-common. Use a bullet with the same intent that matches the
       current mechanism: `SecurityConfig` defines its own `SecurityWebFilterChain`, so
       service-web's shared claims-header reactive chain backs off (Session Gateway is an OAuth2
       Client, not a claims-header resource service). Confirm the back-off in
       `ReactiveClaimsHeaderSecurityConfig` before writing it.
     - Keep `DataSource/JPA auto-configuration excluded (no database required)`. It stays
       accurate with `excludeName`.
   - If step 2 or open item 1 changed a dependency or starter name that `AGENTS.md` mentions,
     update that mention too. Pre-plan review found none.
9. Documentation review. Check every file below against the migrated state, and update any
   statement that a change in this phase made inaccurate, in the same file:
   - `README.md`
   - `docs/*.md`
   - `AGENTS.md` beyond step 8
   - Javadoc and comments in the files this phase changed (for example, a `SecurityConfig` comment
     that names `ObjectMapper`)

   The workflows and `Dockerfile` are review-only (see Scope). Pre-plan review expects no further
   change. Record each file reviewed, and each change made, in the final report.
10. Run `./gradlew clean spotlessApply`, then `./gradlew clean build --warning-mode all`.
11. Fix mechanical migration failures in this repository, using the open items below.
    - If a failure traces to service-common behavior, stop and report the failing test, the Boot 3
      behavior, and the actual result. Include a stack trace when relevant. Do not work around it
      here; the service-common fix needs its own phase.
    - Do the same if a failure needs a change outside Scope or the like-for-like rule, for example
      a redesigned setting or an OAuth2 behavior change. Report it instead of making it.

### Implementation notes

Open items. Confirm each after Gradle resolves the Boot 4 dependencies (step 10, or
`./gradlew dependencies`). Use `unzip -l` / `javap` on the resolved jars and the Boot
`META-INF/spring-configuration-metadata.json`. Record each answer in the final report.

1. OAuth2 client starter:
   - Confirm that `spring-boot-starter-oauth2-client` 4.1.2 is a deprecated alias of
     `spring-boot-starter-security-oauth2-client`, which step 2 then renames like-for-like. If the
     old name is not deprecated and the two differ in content, stop and report.
   - Confirm that the new starter brings `spring-boot-security-oauth2-client`,
     `spring-security-oauth2-client`, and `spring-security-oauth2-jose` 7.1.2. The Boot 3.5.16
     starter brought `spring-security-oauth2-jose` directly. If it is no longer transitive and
     `org.springframework.security.oauth2.jwt` fails to compile, add only
     `org.springframework.security:spring-security-oauth2-jose` without a version, and report it.
2. Spring Security 7.1.2 OAuth2 APIs. Confirm that each still exists with the signature used:
   - `OAuth2ClientConfig`: `WebClientReactiveAuthorizationCodeTokenResponseClient.setWebClient`,
     `DefaultReactiveOAuth2UserService.setWebClient`,
     `OidcReactiveOAuth2UserService.setOauth2UserService`,
     `DefaultServerOAuth2AuthorizationRequestResolver` and `setAuthorizationRequestCustomizer`,
     `OAuth2AuthorizationRequestCustomizers.withPkce()`, and
     `OAuth2AuthorizationRequest.from(...).attributes(...).additionalParameters(...)`.
   - `IdpReactiveJwtDecoderFactory`: `NimbusReactiveJwtDecoder.withJwkSetUri(...).jwsAlgorithm(...)
     .webClient(...)`, `JwtValidators.createDefaultWithValidators`, `OidcIdTokenValidator`,
     `ClaimTypeConverter`, and `ReactiveOidcIdTokenDecoderFactory.createDefaultClaimTypeConverters()`.
   - `RedisAuthorizationRequestRepository`, `ExchangeServerOAuth2AuthorizedClientRepository`, and
     `OAuth2CallbackRedirectResolver`: the `ServerAuthorizationRequestRepository`,
     `ServerOAuth2AuthorizedClientRepository`, `OAuth2ParameterNames`, `PkceParameterNames`, and
     `OidcParameterNames` members they use.
   - If one was removed or renamed, use the documented replacement with the same behavior in the
     same file, and report it. If no like-for-like replacement exists, stop and report.
3. OAuth2 client configuration keys: confirm in the `spring-boot-security-oauth2-client` 4.1.2
   metadata that `spring.security.oauth2.client.registration.*` (`client-id`, `client-secret`,
   `scope`, `authorization-grant-type`, `redirect-uri`, `client-authentication-method`) and
   `spring.security.oauth2.client.provider.*.issuer-uri` are unchanged. Translate a renamed key
   like-for-like in both the main and test `application.yml`. Do not remove an unknown key; report
   it.
4. Spring Security 7 OAuth2 defaults. If `OAuth2ClientConfigIntegrationTest`,
   `SecurityConfigIntegrationTest`, `OAuth2CallbackDiagnosticsIntegrationTest`, or a controller
   test fails because a default changed visible behavior, stop and report. Examples: an extra or
   missing authorization-request parameter, a different PKCE or nonce handling, ID token
   validation, or a redirect status. Report the Boot 3 behavior and the actual result. Do not
   change assertions, and do not add configuration to restore the old behavior. The plan's
   behavior-change list is revised instead.
5. Runtime classpath: report whether `spring-boot-jdbc` and `spring-boot-hibernate` appear on
   `runtimeClasspath`. Absent is expected (the excludes become no-ops). Present means the excludes
   take effect as on Boot 3. Keep `excludeName` either way. If Boot rejects an exclude at startup,
   stop and report.
6. Error handling order. `BrowserErrorRedirectIntegrationTest`, `BrowserErrorRedirectHandlerTest`,
   and the OAuth2 callback handler tests cover the interaction between session-gateway's handlers
   and service-common's `ReactiveErrorWebExceptionHandler` (order `-1`). If one fails with a
   different status, `type`, or redirect, determine whether the cause is service-common or Boot 4
   before acting. A service-common cause stops the phase (step 11).
7. Whether the JaCoCo gates (LINE 0.90, BRANCH 0.65) still pass. If coverage drops because of the
   migration, report it. Do not lower the gates or add tests.

Other notes:

- Let `spotlessApply` order the `tools.jackson` import and wrap long lines.
- Every Boot 4 starter, Testcontainers 2 module, the JUnit launcher, and SpringDoc are
  version-managed through `spring-platform`. Add no versions.
- Boot 4 registers only a Jackson 3 `JsonMapper` bean. Jackson 2 still arrives through SpringDoc,
  so a stray `com.fasterxml` `ObjectMapper` injection compiles but fails at startup. The
  validation `rg` catches this.
- `WebClientConfig` and `OAuth2ClientConfig` build their own `WebClient`s from
  `WebClient.builder()`, so they do not depend on Boot's `WebClient.Builder` bean or on
  `spring.http.codecs.*`.
- These need no change: `TestContainersConfig` (`GenericContainer<?>`, `redis:7-alpine`,
  `@ServiceConnection(name = "redis")`), `WireMockConfig`, `SessionPropertiesTest`
  (`ApplicationContextRunner`), the Redis session classes and Lua scripts, the response records,
  `OpenApiConfig` (`BaseOpenApiConfig`), and the `doesNotExist()` error-body assertions.

### Validation

- Step 1 records a green Boot 3 baseline and its test count.
- `./gradlew clean spotlessApply` succeeds.
- `./gradlew clean build --warning-mode all` succeeds.
  - The summed `tests` count across `build/test-results/test/*.xml` equals the baseline, with 0
    failures and errors. The `skipped` count equals the baseline.
  - The JaCoCo, Checkstyle, and Spotless gates pass.
- This command returns no matches:
  `rg -n 'com\.fasterxml\.jackson\.(core|databind|datatype)|org\.springframework\.boot\.test\.autoconfigure\.web|org\.springframework\.boot\.autoconfigure\.(jdbc|orm)|junitPlatform|starter[-.]test\b|starter[-.]oauth2[-.]client|"org\.testcontainers:junit-jupiter"|^\s+codec:|value\(nullValue\(\)\)' src gradle build.gradle.kts`
- `rg -n 'excludeName' src/main/java/org/budgetanalyzer/sessiongateway/SessionGatewayApplication.java`
  shows both relocated class names.
- `rg -n 'JsonMapper' src/main/java/org/budgetanalyzer/sessiongateway/config/SecurityConfig.java`
  shows the import, field, constructor parameter, and use.
- This command returns no matches:
  `rg -n 'JUnit 5|TestContainers conventions|OAuth2ResourceServerSecurityConfig|brought in by service-common' AGENTS.md README.md docs src`
- Every file reviewed in step 9 is listed in the final report, with its change or "no change".
- `git status --short` lists only files in Scope. It may also list a file changed for open item 2
  or the `spring-security-oauth2-jose` dependency from open item 1, if the report names it.

### Completion criteria

- The worktree has the Scope changes uncommitted, and nothing else.
- The full build passes with the baseline test count. Existing assertions are unchanged.
- `AGENTS.md`, `README.md`, `docs/`, and the comments in changed files describe the migrated state.
  No documentation update is left as follow-up work.
- The final report includes:
  - The baseline and migrated test counts.
  - The answer to each open item.
  - The documentation changes and the files reviewed without change.
  - The deprecation warnings left in place.
  - The behavior-change list from the preamble, with any additions from the open items.

  It states: "Local migration verified; deployed API compatibility pending."

## Phase 2: Record Session-Gateway Migration Status In Service-Common Docs

### Workspace

.

### Goal

Update the service-common Boot 4 consumer notes to show that session-gateway builds and passes its
suite against `0.0.18-SNAPSHOT`. Keep the deployed API gate marked as outstanding.

### Scope

- `docs/plans/spring-boot-4-consumer-migration.md`

### Non-goals

- Editing canonical (version-agnostic) docs, `AGENTS.md`, `README.md`, or any source or build file.
- Recording anything for other consumers.
- Rewriting the consumer integration inventory beyond correcting the session-gateway facts named
  in step 2.

### Required context

- The file in Scope (`## Status`, and the session-gateway bullets under "Consumer integration
  inventory").
- Phase 1 results, read-only:
  - `git -C ../session-gateway status --short`
  - `git -C ../session-gateway log -1 --format=%h`
  - The test count from `../session-gateway/build/test-results/test/*.xml`
  - The Phase 1 final report, including the open-item answers

### Execution steps

1. In `## Status`, after the currency-service paragraph, add a short paragraph:
   - `session-gateway` is migrated locally, uncommitted on top of the recorded HEAD
     ([session-gateway plan](spring-boot-4-session-gateway-migration.md)).
   - Against the Maven Local snapshot, it builds and passes all its existing tests (state the
     count).
   - Name the non-routine changes: `SecurityConfig` uses `JsonMapper`, the DataSource/JPA excludes
     became `excludeName` strings, `spring-boot-starter-oauth2-client` became
     `spring-boot-starter-security-oauth2-client`, and `spring.codec.max-in-memory-size` became
     `spring.http.codecs.max-in-memory-size`. Add any OAuth2 API change Phase 1 reported. Note
     that session-gateway's `AGENTS.md` and docs were updated in the same phase.
   - Nothing has been deployed. State: "Local migration verified; deployed API compatibility
     pending."
   - Still outstanding at the deployed gate: the main and `kubernetes` configuration keys, which
     tests do not exercise, and the OAuth2 login flow against the real IdP.
2. In "Consumer integration inventory":
   - Replace the session-gateway Redis sentence ("Verify that any JSON it writes and reads across a
     rolling deployment keeps the same shape under Jackson 3.") with the finding: it stores only
     flat string hash fields and Lua-script values, persists no JSON, and has no Redis rollout
     item.
   - In the session-gateway Jackson-code bullet, add a short clause that it now uses `JsonMapper`.
   - Keep the wording concise and consistent with the surrounding sections.

### Implementation notes

- This is a release-specific note under `docs/plans/`, so version-specific detail belongs here.
- Read only `../session-gateway`. Do not read orchestration or api-tests.

### Validation

- `git status --short` shows only the Scope file, plus this plan if it is still uncommitted.
- Every changed sentence is consistent with the Phase 1 results.

### Completion criteria

- The consumer notes separate the verified local session-gateway result from the pending deployed
  API compatibility, and no longer list a Redis JSON check for session-gateway.
