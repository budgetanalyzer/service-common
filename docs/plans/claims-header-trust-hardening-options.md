# Claims-Header Trust Hardening Options

## Status

Decision pending.

This document captures the current options for hardening claims-header authentication in
 `service-common`, the concrete scenarios each option protects against, and the operational cost of
 each approach.

## Problem Statement

`service-common` currently creates Spring Security authentication directly from
 `X-User-Id`, `X-Permissions`, and `X-Roles` after structural validation only.

That design assumes a trusted upstream path strips and rewrites those headers before requests reach
 consuming services. In the official orchestration topology, that assumption is enforced at ingress
 by Istio ext_authz header allowlisting plus downstream network and mesh policies. At the shared
 library boundary, however, there is still no independent proof that the claims were minted by the
 trusted auth path.

## Current Orchestration Context

The current `/workspace/orchestration` model already provides meaningful protection:

- Public ingress spoofing is mitigated by Istio ext_authz `headersToUpstreamOnAllow`. Only the
  allowlisted identity headers from ext_authz are forwarded to upstream services.
- The Phase 3 ingress verifier proves that forged external `X-User-Id`, `X-Roles`, and
  `X-Permissions` headers are overwritten before the backend sees them.
- `nginx-gateway`, `ext-authz`, and `session-gateway` only accept traffic from the Istio ingress
  gateway.
- Backend services only accept traffic from `nginx-gateway`, with the existing
  `permission-service` exception for `session-gateway`.

This means the nominal production path is already defended outside the application layer.

## Residual Scenarios

The remaining value of app-layer hardening is in failure, drift, and bypass scenarios:

1. A consuming service is exposed directly without the intended ingress strip-and-rewrite path.
2. A new ingress or proxy route forwards user-supplied claims headers instead of overwriting them.
3. A debug path, port-forward, or ad hoc deployment bypasses the trusted ingress path.
4. An in-cluster direct path becomes reachable because `NetworkPolicy`, mesh policy, or routing
   drifts from the intended topology.
5. An intermediate trusted hop mutates `X-User-Id`, `X-Roles`, or `X-Permissions` before the
   request reaches the service.

## Option 1: Documentation Only

Keep the current behavior and tighten documentation so the trust boundary is explicit.

### What It Looks Like

- Keep claims-header authentication default-on in `service-common`.
- Document that ingress must strip and rewrite auth headers before any request reaches a service.
- Document that direct service exposure or alternate ingress paths are unsafe unless they preserve
  the same guarantees.

### What It Mitigates

- Misunderstanding of the deployment contract.

### What It Does Not Mitigate

- No direct technical protection against spoofed headers if the deployment contract is violated.

### Consumer Impact

- No consuming-service code or configuration changes.

### Pros

- Lowest cost.
- Preserves current compatibility and rollout model.

### Cons

- No new control.
- Leaves the library boundary dependent on external enforcement only.

## Option 2: Explicit Opt-In

Require a service-level property to enable claims-header authentication.

### What It Looks Like

- Claims-header security becomes disabled by default unless a property such as
  `budgetanalyzer.service.security.claims-header.enabled=true` is set.
- The shared auto-configuration fails closed when the property is absent.

### What It Mitigates

- Accidental trust in claims headers when a service is deployed outside the intended ingress model.
- Silent inheritance of the claims-header trust contract by new consuming services.

### What It Does Not Mitigate

- Once enabled, the service still trusts any structurally valid claims headers.
- Does not prove the claims were minted by ext_authz.
- Does not protect against a compromised or misconfigured trusted upstream once the property is on.

### Consumer Impact

- All current consuming services that rely on shared claims-header security must be updated to set
  the property explicitly.
- Tests and service templates must adopt the new property.

### Pros

- Cheap operational guardrail.
- Makes the trust assumption explicit in service configuration and code review.

### Cons

- Modest security gain only.
- Breaking behavior change for current consumers.
- Not sufficient by itself for meaningful defense in depth.

## Option 3: Mandatory Shared Secret Header

Require a second header carrying a shared secret known only to trusted internal hops.

### What It Looks Like

- ext_authz or NGINX injects a header such as `X-Internal-Auth`.
- `service-common` rejects claims headers unless the secret matches a configured value.
- Istio and NGINX must forward the new header alongside the current identity headers.

### What It Mitigates

- Direct calls to a backend if the service is accidentally exposed and the caller does not know the
  shared secret.
- Some deployment mistakes where the trusted path was bypassed entirely.

### What It Does Not Mitigate

- A trusted intermediate hop that can still inject the secret.
- A route that bypasses ext_authz but still traverses a component allowed to add the secret.
- Mutation of claims by a trusted component that also knows the secret.

### Consumer Impact

- Coordinated changes required in orchestration and all consuming services.
- Secret distribution, rotation, and test fixtures all become more complex.

### Pros

- Stronger than explicit opt-in.
- Detects some direct-path spoofing even when surrounding controls drift.

### Cons

- Adds operational complexity and secret management.
- Proves only that the request passed through a trusted hop, not that the claims themselves are
  authentic.
- Limited improvement relative to the orchestration controls already in place.

## Option 4: Mandatory Signed Internal Auth Context

Require ext_authz to mint a signed internal auth context that services verify before creating
 authentication.

### What It Looks Like

- ext_authz emits either a single signed token header or a canonical auth-context header plus a
  signature header.
- The signed payload includes at minimum `userId`, roles, permissions, issued-at time, expiry, and
  an audience or service binding if needed.
- `service-common` verifies the signature and expiry before constructing Spring Security
  authentication.
- The existing plain claims headers can either be removed entirely from the trust decision or kept
  only as derived values after signature verification.

### What It Mitigates

- Direct backend spoofing even if the backend becomes reachable.
- Direct NGINX spoofing if the route bypasses ext_authz.
- Intermediate mutation of `X-User-Id`, `X-Roles`, or `X-Permissions`.
- Misconfigurations where the transport path still exists but the trusted auth component was not
  actually involved.

### What It Does Not Mitigate

- A fully compromised signing authority.
- General service-side authorization bugs such as returning another user's data after successful
  authentication.

### Consumer Impact

- Coordinated changes required in orchestration and all consuming services.
- `service-common` test utilities and consuming-service tests must be updated to generate valid
  signed auth context for integration tests.
- Key distribution becomes part of the platform contract.

### Pros

- Real application-layer authenticity control.
- Best defense-in-depth option if the goal is to survive direct-path spoofing, topology drift, or
  intermediate header tampering.
- Clear separation between authenticated identity material and plain forwarded headers.

### Cons

- Highest implementation and rollout cost.
- More moving parts: signing, verification, key rotation, clock-skew handling, and test support.
- Partly duplicates protections already enforced by the orchestration ingress model.

## Recommended Direction

If the goal is only to make the trust assumption explicit, explicit opt-in is enough.

If the goal is actual defense in depth, explicit opt-in is not enough. It is only a deployment
 guardrail. The only option in this set that materially improves authenticity at the library
 boundary is a mandatory signed internal auth context minted by ext_authz and verified in
 `service-common`.

That makes the practical recommendation:

1. Do not treat explicit opt-in as the substantive security fix.
2. If work is undertaken for true defense in depth, target signed internal auth context.
3. If that coordination cost is not justified right now, defer the change and rely on the current
   orchestration controls plus clear documentation of the trust contract.

## Decision Notes

- The official orchestration topology already mitigates external header spoofing on the public
  ingress path.
- Current production value from additional library hardening would come from surviving drift,
  bypass, or partial-control failure rather than fixing the nominal ingress flow.
- Because this is a cross-repository decision, implementation should be coordinated with
  orchestration and all current claims-header-consuming services.
