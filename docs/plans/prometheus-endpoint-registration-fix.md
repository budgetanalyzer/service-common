# Fix Prometheus Endpoint Registration in Consumer Services

Date: 2026-04-11
Branch: `prometheus-registration-fixes`

## Status

Code changes applied and verified by integration tests in `service-common`.
The residual merge-logic bug in `PrometheusEndpointPostProcessor` has also
been fixed on this branch by applying Option A (see below). Remaining work
is the publish + rebuild + redeploy cycle that makes the fix reach the four
running Spring Boot workloads in the KIND cluster.

## Problem

All four Spring Boot services deployed by `/workspace/orchestration` return
404 on `/actuator/prometheus`. The observed errors are:

```
org.springframework.web.servlet.resource.NoResourceFoundException:
  No static resource actuator/prometheus.
    at ResourceHttpRequestHandler.handleRequest(ResourceHttpRequestHandler.java:585)
```

and, for the reactive `session-gateway`:

```
org.springframework.web.reactive.resource.NoResourceFoundException:
  404 NOT_FOUND "No static resource actuator/prometheus."
    at ResourceWebHandler.lambda$handle$1(ResourceWebHandler.java:434)
```

`NoResourceFoundException` is Spring's fallback when no handler is mapped to a
path; the request reaches the app but nothing knows about the URL, so it hits
the static resource handler, which has no such file. This means the
Prometheus scrape endpoint bean is not registered, i.e. the actuator endpoint
is not exposed.

## Root cause

Two independent problems in the main branch combined to suppress the endpoint:

1. `PrometheusEndpointPostProcessor` was registered via
   `service-core/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports`.
   Spring Boot 3.x loads `EnvironmentPostProcessor` implementations through
   `SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class, ...)`,
   which reads `META-INF/spring.factories` only. The `.imports` file format
   is supported for the `AutoConfiguration` extension point and nothing else,
   so this registration file was inert. The post-processor never ran,
   `management.endpoints.web.exposure.include` was never populated with
   `prometheus`, and the scrape endpoint stayed unmapped at runtime.

2. Even if the post-processor had been loaded, it never set
   `management.prometheus.metrics.export.enabled`, which must be `true` for
   `micrometer-registry-prometheus` to register the Prometheus meter registry
   bean at all. With Spring Boot 3.5, the default for this property is
   `false` when a consumer does not opt in.

Neither consumer service sets either of these properties in its
`application.yml` or in its Kubernetes env, so both defaults have to come
from `service-common`.

## Changes on branch `prometheus-registration-fixes`

Already committed on this branch:

- Commit `bc66789` - `set management.metrics.tags.application={spring.application.name} as a default`
  - Adds `service-core/src/main/java/org/budgetanalyzer/core/config/ApplicationMetricTagPostProcessor.java`.
    It sets `management.metrics.tags.application=${spring.application.name}` in
    a low-priority property source so every Micrometer meter carries the
    service identity. This satisfies the dashboard label contract in the
    orchestration plan `fix-grafana-jvm-spring-dashboards-2026-04-10.md`
    Phase 3 at the application layer rather than at scrape time.
  - Adds `ApplicationMetricTagPostProcessorTest`.

- Commit `d4c8c58` - `Created META-INF/spring.factories and removed the inert .imports file ...`
  - Renames
    `service-core/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports`
    to
    `service-core/src/main/resources/META-INF/spring.factories`,
    changing the format from the `.imports` list to the classic
    `org.springframework.boot.env.EnvironmentPostProcessor=\
      org.budgetanalyzer.core.config.PrometheusEndpointPostProcessor,\
      org.budgetanalyzer.core.config.ApplicationMetricTagPostProcessor`
    key that Spring Boot 3.x actually reads for this extension point.
  - Updates `PrometheusEndpointPostProcessor` to also set
    `management.prometheus.metrics.export.enabled=true` in its low-priority
    `prometheusDefaults` property source. Consumers can still override the
    value explicitly.
  - Deletes a phantom `./META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
    file that lived at the repository root rather than inside any module's
    `src/main/resources`. That file was never packaged into any jar and was
    not doing anything - confirmed by inspecting `main` and noting there is
    no corresponding build resource entry.
  - Adds `PrometheusEndpointServletIntegrationTest` and
    `PrometheusEndpointReactiveIntegrationTest` that boot real Spring Boot
    applications (servlet and reactive) through `ServletTestApplication` and
    `ReactiveTestApplication` and assert that
    - `GET /actuator/prometheus` returns HTTP 200.
    - The body contains `jvm_info`.
    - `management.endpoints.web.exposure.include` resolves to a value
      containing `prometheus`.

The legitimate `service-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
file is **not** touched. It still contains
`ServiceCoreAutoConfiguration` and `ServiceCoreJpaAutoConfiguration` and
continues to drive component scanning plus the JPA auditor bean. Same for
`service-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

## Verification already done

`./gradlew :service-core:test :service-web:test` is green on the branch.
The new integration tests directly prove the end-to-end behavior we care
about: a Spring Boot app that consumes service-common exposes
`/actuator/prometheus` and the response contains live JVM metrics, with no
extra consumer configuration.

## Remaining work

These steps have to run outside `service-common` to get the fix into the
running cluster. They are the only reason the error is still visible today.

1. Publish service-common to the local Maven repository.

   ```bash
   cd /workspace/service-common
   ./gradlew publishToMavenLocal
   ```

   Expected: fresh artifacts at
   `~/.m2/repository/org/budgetanalyzer/service-core/0.0.1-SNAPSHOT/` and
   `~/.m2/repository/org/budgetanalyzer/service-web/0.0.1-SNAPSHOT/`, both
   containing the new `META-INF/spring.factories` entry. Inspect one jar to
   confirm:

   ```bash
   unzip -p ~/.m2/repository/org/budgetanalyzer/service-core/0.0.1-SNAPSHOT/service-core-0.0.1-SNAPSHOT.jar \
     META-INF/spring.factories
   ```

   Expect the `org.springframework.boot.env.EnvironmentPostProcessor=...`
   entry with both post-processor class names.

2. Rebuild each consumer service image and redeploy it.

   From `/workspace/orchestration` under Tilt:

   ```bash
   tilt trigger service-common-publish   # if orchestration wraps step 1 as a resource
   tilt trigger transaction-service
   tilt trigger currency-service
   tilt trigger permission-service
   tilt trigger session-gateway
   ```

   If `service-common-publish` is not a Tilt resource in orchestration, run
   step 1 manually and then trigger the four services.

3. Confirm each pod now exposes the endpoint.

   For each service, exec into the pod and curl the local actuator path,
   honoring each service's servlet context path:

   ```bash
   kubectl exec deploy/currency-service -c currency-service -- \
     curl -fsS http://localhost:8084/currency-service/actuator/prometheus | head -20
   kubectl exec deploy/transaction-service -c transaction-service -- \
     curl -fsS http://localhost:8082/transaction-service/actuator/prometheus | head -20
   kubectl exec deploy/permission-service -c permission-service -- \
     curl -fsS http://localhost:8086/permission-service/actuator/prometheus | head -20
   kubectl exec deploy/session-gateway -c session-gateway -- \
     curl -fsS http://localhost:8081/actuator/prometheus | head -20
   ```

   Expect OpenMetrics output starting with `# HELP`. If any service still
   returns 404, the build or the image rebuild did not pick up the new
   `service-common` jar - go back to step 1 and verify the jar contents.

4. Confirm Prometheus scrapes succeed.

   ```bash
   kubectl -n monitoring exec deploy/prometheus-kube-prometheus-prometheus \
     -c prometheus -- wget -qO- http://localhost:9090/api/v1/targets \
     | grep -E 'session-gateway|currency-service|permission-service|transaction-service'
   ```

   Expect `"health":"up"` for all four. If scrapes are still `502`, that is
   the mesh/scrape-transport problem and belongs to
   `orchestration/docs/plans/fix-grafana-jvm-spring-dashboards-2026-04-10.md`
   Phase 2, not to this repo.

## Residual issue: resolved (Option A applied)

`PrometheusEndpointPostProcessor.postProcessEnvironment` previously read the
current value of `management.endpoints.web.exposure.include` via
`environment.getProperty(...)`, parsed it into a set, added `prometheus`, and
published the **merged** value in a property source added via `addLast`.
Because `addLast` puts the source at the bottom of the precedence chain, any
earlier source that already held the original value still won, so the merge
branch was effectively dead code:

- Consumer sets nothing: `prometheusDefaults` was the only source holding the
  key, so the default `health,prometheus` won. Worked correctly because none
  of the four current consumers set the property.
- Consumer sets `management.endpoints.web.exposure.include=health,info`: the
  merged value computed by the post-processor was `health,info,prometheus`
  but it was dropped to the lowest precedence, so the effective resolved
  value was still `health,info` - **prometheus silently disappeared**.

This was a latent regression waiting for the first consumer to opt in with
its own exposure list. Two options were considered:

Option A. Drop the merge logic. Always publish a static default
`management.endpoints.web.exposure.include=health,prometheus` via `addLast`.
Document that any consumer that sets the property itself is responsible for
keeping `prometheus` in the list.

Option B. Keep the merge logic, but add the merged value via `addFirst` so
it overrides the consumer value. More surprising and violates the existing
Javadoc promise that explicit consumer settings take precedence.

**Option A was applied on this branch.** The changes:

- `parseEndpoints`, the merge logic, and the `existing` read were removed
  from `PrometheusEndpointPostProcessor`.
- The post-processor now publishes a fixed
  `Map.of(EXPOSURE_PROPERTY, "health,prometheus", PROMETHEUS_EXPORT_ENABLED, "true")`
  via `addLast`.
- The class Javadoc states the new contract explicitly: consumers that set
  `management.endpoints.web.exposure.include` themselves are responsible for
  keeping `prometheus` in the list.
- `PrometheusEndpointPostProcessorTest` was updated: the merge-specific tests
  (`shouldNotDuplicatePrometheusIfAlreadyPresent`,
  `shouldHandleWhitespaceInExistingConfig`) are gone, replaced by
  `shouldNotMergePrometheusIntoConsumerExposureList` which explicitly asserts
  that a consumer value without `prometheus` stays without `prometheus`.

`ApplicationMetricTagPostProcessor` has the same `addLast` pattern but no
merge logic, so it does not have this latent bug. Left as is.

## Acceptance criteria

- `./gradlew :service-core:test :service-web:test` is green, including the
  two new Prometheus integration tests.
- Curling `/actuator/prometheus` from inside each of the four pods returns
  HTTP 200 and a body containing `jvm_info`.
- Prometheus `/api/v1/targets` reports all four Spring Boot targets as
  `up`.
- If the residual-issue follow-up is applied, the updated unit tests reflect
  the new contract and still pass.

## Out of scope

- Orchestration-side changes (ServiceMonitor relabeling to service DNS,
  mTLS-compatible scrape transport, dashboard `application` label wiring).
  Those are tracked by
  `orchestration/docs/plans/fix-grafana-jvm-spring-dashboards-2026-04-10.md`
  Phases 2 through 4 and belong to `/workspace/orchestration`.
- The PostgreSQL `Connection refused` error observed in `transaction-service`
  logs alongside the 404s is a separate infrastructure issue.
