# Security Consensus Hardening Evidence

## 2026-11-07: Trusted HTTP client address

### Implementation and trust boundary

Servlet HTTP logging now reads the client address only from `X-Real-IP`, falling back to
`HttpServletRequest.getRemoteAddr()` when that header is absent, blank, or `unknown`. It no longer
uses `X-Forwarded-For` or the other forwarding headers that callers can populate. Unit coverage
proves the NGINX-provided header wins and a forged `X-Forwarded-For` value cannot replace the
direct peer address.

The trusted value is established outside this repository. The orchestration configuration at
`../orchestration/nginx/nginx.k8s.conf` trusts only its pod-local Envoy hop when deriving
`$remote_addr` from the rightmost ingress-appended `X-Forwarded-For` entry. Every API backend route
includes `../orchestration/nginx/includes/backend-headers.conf`, which overwrites `X-Real-IP` with
that derived `$remote_addr`. This phase did not change ingress address derivation.

### Reactive behavior

`ReactiveHttpLoggingFilter` does not read forwarding headers. It logs a client address only when
`ServerHttpRequest.getRemoteAddress().getAddress()` resolves to a numeric address. Session Gateway
enables Spring's framework forwarded-header handling, whose `ForwardedHeaderTransformer` leaves the
forwarded remote address unresolved on the ingress path. `getAddress()` is therefore null and the
filter omits `clientIp`. This is a missing value rather than a forged value. The filter deliberately
does not call `getHostString()`, because that would log the caller-controlled forwarded hostname.

### Validation

- `./gradlew :service-web:test --tests org.budgetanalyzer.service.servlet.http.ContentLoggingUtilTest`
  passed all 25 focused tests.
- `./gradlew clean spotlessApply` passed.
- Final `./gradlew clean build` passed all unit, integration, JaCoCo, Checkstyle, Spotless, servlet
  smoke, and reactive smoke gates without Checkstyle violations. The first full run exposed two
  test-name warnings; renaming those tests removed the warnings before the final run.
- JaCoCo final totals were 312/349 lines and 172/218 branches for `service-core`, and 1137/1193
  lines and 297/359 branches for `service-web`, above the configured gates.
- `./gradlew publishToMavenLocal` published all four `0.1.1-SNAPSHOT` artifacts for later phases.
- `git diff --check` passed.

### Remaining limitation

Reactive Session Gateway requests behind the ingress do not currently include a client address in
shared HTTP logs. Preserving omission is safer than recording an untrusted forwarded value; closing
that observability gap requires a separately designed trusted-address path.

## 2026-11-07: Structured service audit events

### Implementation and data boundary

`service-web` now exposes `AuditEvent` and an injectable `AuditLogger` for both servlet and reactive
applications. Events are emitted at INFO as compact JSON on the dedicated `budgetanalyzer.audit`
logger. The six fields are timestamp, actor, operation, resource identifier, outcome, and
correlation ID.

Servlet applications automatically register an interceptor for mapped controller `POST`, `PUT`,
`PATCH`, and `DELETE` requests. It emits once after completion, uses the Spring route pattern rather
than the raw URL, joins only route path-variable values for resource identity, and records the
actual response status. It does not inspect bodies, query strings, email, display name, or other
profile attributes. Reactive applications receive the same logger bean and event type but emit
their session-lifecycle events explicitly.

### Validation

- `./gradlew :service-web:test --tests
  org.budgetanalyzer.service.servlet.audit.AuditRequestInterceptorTest --tests
  org.budgetanalyzer.service.config.AuditAutoConfigurationTest` passed all five focused tests.
  They verify exact-one emission for all four state-changing methods, exclusion of reads and
  unmapped requests, safe field structure, servlet registration, and reactive logger availability.
- The privacy request includes distinct email and display-name values in its body, query string,
  and authentication details; none appear in the emitted event.
- `./gradlew clean build` passed all unit, integration, Javadoc, Checkstyle, Spotless, JaCoCo,
  servlet smoke, and reactive smoke gates. Final JaCoCo totals were 312/349 lines and 172/218
  branches for `service-core`, and 1186/1247 lines and 305/373 branches for `service-web`.
- `./gradlew publishToMavenLocal` published all four `0.1.1-SNAPSHOT` artifacts for later phases.
- `git diff --check` passed.

### Remaining limitations

Retention, access control, and destination routing for `budgetanalyzer.audit` require a hosted log
store and remain deployment concerns. Read requests are intentionally outside the audit scope.
