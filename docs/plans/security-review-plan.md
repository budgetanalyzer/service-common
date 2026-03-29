# Security And Correctness Review Plan

## Summary

This document captures the current security and correctness issues identified in `service-common`
 and the recommended remediation sequence. It is intentionally review-only: no code changes are
 included here.

Build validation at review time:

```bash
./gradlew clean build
```

Result: passed.

## Findings

### 1. HTTP body logging is not sanitized

**Severity**: High

When request or response body logging is enabled, both servlet and reactive implementations append
 raw body content directly to log output. JSON payloads are not sanitized before logging, despite
 comments implying otherwise.

**Risk**:
- Passwords, API keys, bearer tokens, session data, and PII can be written to logs
- Shared-library impact is broad because this behavior propagates to all consuming services

**Affected files**:
- `service-web/src/main/java/org/budgetanalyzer/service/servlet/http/ContentLoggingUtil.java`
- `service-core/src/main/java/org/budgetanalyzer/core/logging/HttpLogFormatter.java`
- `service-web/src/main/java/org/budgetanalyzer/service/servlet/http/HttpLoggingFilter.java`
- `service-web/src/main/java/org/budgetanalyzer/service/reactive/http/ReactiveHttpLoggingFilter.java`

### 2. Servlet security does not back off for consumer-defined security chains

**Severity**: High

The servlet claims-header auto-configuration always registers its `SecurityFilterChain`. The
 reactive implementation already backs off when a service defines its own chain, but the servlet
 implementation does not.

**Risk**:
- Ambiguous or competing security filter chains in consuming services
- Unexpected rule ordering or accidental exposure when services add their own security config

**Affected files**:
- `service-web/src/main/java/org/budgetanalyzer/service/security/ClaimsHeaderSecurityConfig.java`
- `service-web/src/main/java/org/budgetanalyzer/service/security/ReactiveClaimsHeaderSecurityConfig.java`

### 3. Claims-header authentication relies entirely on trusted network position

**Severity**: High

Authentication is created directly from `X-User-Id`, `X-Permissions`, and `X-Roles` after only
 structural validation. There is no cryptographic verification and no secondary trust signal at
 the library boundary.

**Risk**:
- If any consuming service is reachable without a proxy that strips and rewrites these headers,
  authentication can be forged
- The library makes a strong deployment assumption that is easy to violate accidentally

**Affected files**:
- `service-web/src/main/java/org/budgetanalyzer/service/security/ClaimsHeaderAuthenticationFilter.java`
- `service-web/src/main/java/org/budgetanalyzer/service/security/ReactiveClaimsHeaderSecurityConfig.java`

### 4. Client IP logging trusts spoofable forwarded headers by default

**Severity**: Medium

Servlet request logging reads `X-Forwarded-For` and similar headers directly and `includeClientIp`
 defaults to `true`.

**Risk**:
- Logged client IP can be attacker-controlled outside a trusted proxy setup
- Audit and investigation value of these logs is reduced

**Affected files**:
- `service-web/src/main/java/org/budgetanalyzer/service/config/HttpLoggingProperties.java`
- `service-web/src/main/java/org/budgetanalyzer/service/servlet/http/ContentLoggingUtil.java`

### 5. Negative `max-body-size` values are accepted and break servlet logging

**Severity**: Medium

Negative values bind successfully into configuration. The reactive path clamps them to zero, but
 the servlet body extraction path passes the negative value through and can throw at runtime.

**Risk**:
- Runtime logging failures when body logging is enabled
- Inconsistent behavior between servlet and reactive stacks

**Affected files**:
- `service-web/src/main/java/org/budgetanalyzer/service/config/HttpLoggingProperties.java`
- `service-web/src/main/java/org/budgetanalyzer/service/servlet/http/ContentLoggingUtil.java`
- `service-web/src/main/java/org/budgetanalyzer/service/reactive/http/CachedBodyServerHttpRequestDecorator.java`
- `service-web/src/test/java/org/budgetanalyzer/service/servlet/http/HttpLoggingPropertiesTest.java`

### 6. `SoftDeleteOperations` is not actually generic over ID type

**Severity**: Medium

`SoftDeleteOperations` presents itself as reusable shared infrastructure, but `findByIdActive`
 hardcodes `Long` instead of supporting the repository’s actual identifier type.

**Risk**:
- Shared-library API does not scale to non-`Long` identifiers
- Consumers will need local workarounds or duplicate APIs

**Affected files**:
- `service-core/src/main/java/org/budgetanalyzer/core/repository/SoftDeleteOperations.java`

## Recommended Change Plan

### Phase 1: Immediate security fixes

1. Fix HTTP body logging safety for both servlet and reactive stacks.
2. Add body redaction for common secret field names in structured payloads.
3. Suppress or replace logging for multipart, binary, and compressed content.
4. Correct misleading comments and documentation that currently imply body sanitization exists.

### Phase 2: Security composition hardening

1. Make servlet security auto-configuration back off when a consuming service defines its own
   `SecurityFilterChain`.
2. Add tests proving custom consumer security configuration overrides the shared default
   deterministically.
3. Review whether duplicate bean registration patterns in web auto-configuration should be reduced
   to avoid accidental ambiguity.

### Phase 3: Claims-header trust hardening

1. Decide whether claims-header security should remain default-on or become explicit opt-in.
2. If kept, add defense-in-depth such as an explicit trust toggle or secondary trust signal.
3. Tighten documentation so consumers know ingress must strip and rewrite auth headers before
   requests reach services.
4. Add tests for failure modes around partial or malformed trust configuration assumptions where the
   library can realistically enforce them.

### Phase 4: Logging correctness hardening

1. Stop trusting forwarded IP headers by default, or gate that behavior behind an explicit
   `trust-forwarded-headers` style property.
2. Validate or clamp `max-body-size` consistently across both stacks.
   Current implementation choice: clamp negative configured values to `0` at property binding,
   which disables body capture without reviving the forwarded-header work.
3. Add regression tests for negative, zero, and oversized body-size settings in real filter paths.

### Phase 5: Shared API correctness

1. Generalize `SoftDeleteOperations` over ID type instead of hardcoding `Long`.
2. Add tests covering non-`Long` identifier usage to keep the shared API honest.
3. Update docs to reflect the true generic contract.

## Suggested Implementation Order

1. HTTP body logging sanitization and content-type restrictions
2. Servlet security backoff behavior
3. Claims-header trust hardening decision and documentation
4. Logging property validation and client IP trust model
5. `SoftDeleteOperations` generic ID cleanup

## Test Plan For Future Implementation

When implementation starts, add or update tests for:

- Servlet and reactive body logging redaction behavior
- Binary, multipart, and compressed body logging suppression
- Servlet custom `SecurityFilterChain` override/backoff behavior
- Claims-header failure and trust-configuration edge cases
- Negative and zero `max-body-size` behavior
- Non-`Long` soft-delete repository ID support

## Notes

- This plan is based on repository review plus a successful `./gradlew clean build`.
- The claims-header issue is an architectural trust-boundary risk in the library design. Exploitability
  in production depends on consuming service deployment topology and ingress enforcement.
