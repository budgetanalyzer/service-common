# Task: Investigate OpenTelemetry App-Level Instrumentation For Spring Services

  ## Context

  The orchestration repo now has Phase 7.4 mesh tracing wired:

  - Istio Envoy sidecars export traces via OTLP/gRPC.
  - Jaeger runs in `monitoring`.
  - Istio provider:
    - `jaeger`
    - `jaeger-collector.monitoring.svc.cluster.local:4317`
  - Current traces show proxy/mesh spans such as ingress and NGINX hops.

  What is missing is application-level spans inside the Spring Boot services.

  Current service-common role:
  - Shared Java/Spring Boot library consumed by:
    - `transaction-service`
    - `currency-service`
    - `permission-service`
    - `session-gateway`
  - It already owns shared backend conventions and should be the first place to evaluate common observability defaults.

  ## Goal

  Investigate adding standard OpenTelemetry application instrumentation through `service-common`, without adding vendor-
  specific Jaeger client libraries.

  Use OpenTelemetry-standard instrumentation and OTLP export.

  Target runtime shape:

  ```text
  Spring Boot service
    -> OpenTelemetry Java instrumentation / Micrometer tracing
    -> OTLP/gRPC or OTLP/HTTP
    -> jaeger-collector.monitoring.svc.cluster.local
    -> Jaeger UI

  ## Questions To Answer

  1. What is the best integration path for Spring Boot 3 services?
      - OpenTelemetry Java agent?
      - Micrometer Tracing bridge to OpenTelemetry?
      - OpenTelemetry SDK auto-config?
      - Explicit application spans via shared helpers?
      - Combination of the above?
  2. Can service-common provide sane defaults without forcing every service to duplicate config?
  3. What spans would we get automatically?
      - HTTP server spans
      - outbound HTTP client spans
      - JDBC spans
      - RabbitMQ spans
      - Redis spans
      - Spring Security / WebFlux behavior for session-gateway
  4. What would still require explicit custom spans?
  5. What environment variables or Spring properties should orchestration set?
     Likely candidates:
      - OTEL_SERVICE_NAME
      - OTEL_EXPORTER_OTLP_ENDPOINT
      - OTEL_EXPORTER_OTLP_PROTOCOL
      - OTEL_TRACES_EXPORTER
      - OTEL_METRICS_EXPORTER
      - OTEL_LOGS_EXPORTER
      - OTEL_RESOURCE_ATTRIBUTES
  6. Should services export directly to Jaeger’s OTLP receiver, or should orchestration add an OpenTelemetry Collector
     first?
      - Direct to Jaeger is simpler.
      - Collector is more production-standard and vendor-neutral.
  7. What is the minimal first implementation that proves value without overbuilding?

  ## Constraints

  - Do not use deprecated Jaeger client libraries.
  - Prefer OpenTelemetry/OTLP standards.
  - Avoid vendor lock-in to New Relic, Datadog, etc.
  - Do not break existing Micrometer/Prometheus metrics.
  - Avoid requiring service repos to copy large config blocks.
  - Keep local Tilt/Kind and OCI/k3s parity in mind.
  - If orchestration config is required, document exactly what env vars/manifests need to change, but do not implement
    orchestration changes from the service-common repo.

  ## Suggested Investigation Path

  1. Inspect current service-common Spring Boot dependencies and auto-config.
  2. Inspect consuming services for:
      - servlet vs WebFlux
      - JDBC usage
      - Redis client usage
      - RabbitMQ client usage
      - HTTP clients used, if any
  3. Compare two likely approaches:
      - OpenTelemetry Java agent attached at runtime
      - Micrometer Tracing + OTLP exporter through Spring Boot dependencies
  4. Build a small proof in one service, preferably transaction-service.
  5. Confirm Jaeger shows app-level spans nested with Istio proxy spans.
  6. Recommend the lowest-maintenance path for all backend services.

  ## Expected Deliverable

  Produce a short design note with:

  - Recommended approach
  - Required service-common dependency/config changes
  - Required per-service changes, if any
  - Required orchestration env/config changes
  - Example trace expected in Jaeger
  - Risks and tradeoffs
  - Follow-up implementation steps


  My instinct: start by comparing **Java agent vs Micrometer Tracing**. For this architecture, the Java agent may be the
  fastest proof because it can auto-instrument HTTP/JDBC/etc. without changing each service much. But `service-common` may
  be the better long-term place for shared semantic conventions and explicit spans where auto-instrumentation is too
  generic.