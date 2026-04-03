# Reactive ErrorWebExceptionHandler And Shared ApiExceptionHandler Plan

## Status

Phases 1-6 are implemented in `service-web`.

This plan defines the `service-common` prerequisite referenced by
`/workspace/session-gateway/docs/plans/fix-error-contract-and-test-gaps.md`.

The implementation must stay simple:

- Share exception-to-error-contract resolution.
- Keep separate servlet and reactive adapter classes.
- Do not build a cross-framework "mega handler".
- Do not introduce new modules, reflection, or generic callback abstractions.

If implementation starts to require a larger abstraction than this document describes, stop and ask
for guidance before continuing.

## Goal

Make the API error contract reusable in both controller-level and reactive filter-level paths while
keeping the implementation small and backwards compatible.

Specifically:

- Add a shared way to map common exceptions to `HttpStatusCode` plus `ApiErrorResponse`.
- Add a reactive `ErrorWebExceptionHandler` that renders the shared JSON contract for filter-level
  failures.
- Reuse the same shared mapping in both `ReactiveApiExceptionHandler` and
  `ServletApiExceptionHandler`.

## Non-Goals

- Do not replace servlet and reactive handlers with a single concrete class.
- Do not move to a new package structure.
- Do not change `ApiErrorResponse`, `ApiErrorType`, or the existing public exception classes.
- Do not expand scope into HTML rendering, browser redirects, or service-specific routing logic.
- Do not change the lockstep-upgrade compatibility model.

## Constraints

The plan must respect the current `service-web` dependency model:

- Servlet and WebFlux remain `compileOnly` dependencies for consumers.
- Shared code in `org.budgetanalyzer.service.api` must avoid direct dependence on
  `MethodArgumentNotValidException` or `WebExchangeBindException`.
- Shared code may depend on neutral Spring contracts already common to both stacks, such as
  `HttpStatusCode`, `ResponseStatusException`, and `BindingResult`.

This keeps the shared layer reusable without coupling it to one web stack.

## Current State

Today the shared interface only centralizes response builders:

- `service-web/src/main/java/org/budgetanalyzer/service/api/ApiExceptionHandler.java`

The actual exception-to-status dispatch is duplicated:

- `service-web/src/main/java/org/budgetanalyzer/service/reactive/api/ReactiveApiExceptionHandler.java`
- `service-web/src/main/java/org/budgetanalyzer/service/servlet/api/ServletApiExceptionHandler.java`

Reactive filter-level failures still have no shared JSON contract renderer in `service-common`.

## Design Direction

Use one shared resolution layer and three thin adapters:

1. Shared support in `ApiExceptionHandler`.
2. `ReactiveApiExceptionHandler` for controller exceptions.
3. `ReactiveErrorWebExceptionHandler` for filter-level exceptions.
4. `ServletApiExceptionHandler` for MVC controller exceptions.

The adapters stay framework-specific. The error mapping stays shared.

## Phase 1: Add Shared Resolution Support

### Files

- `service-web/src/main/java/org/budgetanalyzer/service/api/ApiExceptionHandler.java`

### Changes

Extend `ApiExceptionHandler` with small shared helpers only.

Add:

- `record ResolvedError(HttpStatusCode statusCode, ApiErrorResponse response)`
- `default ResponseEntity<ApiErrorResponse> toResponseEntity(ResolvedError resolvedError)` or an
  equivalent helper on `ResolvedError`
- `default ResolvedError resolveCommonException(Throwable throwable)`
- `default ResolvedError resolveValidationFailure(BindingResult bindingResult)`
- `default List<FieldError> extractFieldErrors(BindingResult bindingResult)`
- `default ResolvedError resolveResponseStatus(ResponseStatusException exception)`

`resolveCommonException(Throwable throwable)` should handle only the exceptions shared by both
stacks:

- `ResourceNotFoundException`
- `InvalidRequestException`
- `BusinessException`
- `ClientException`
- `ServiceUnavailableException`
- `AccessDeniedException`
- `AuthorizationDeniedException`
- `AuthenticationException`
- `ResponseStatusException`
- generic `Exception`

Keep servlet-only and reactive-only exception normalization out of this method.

### Simplicity Guardrail

Do not add strategy interfaces, registries, maps of lambdas, or reflection-based dispatch.
Use a straightforward `if` chain or `switch` on known exception types.

### Tests

Add a focused unit test for the shared interface behavior, for example:

- `service-web/src/test/java/org/budgetanalyzer/service/api/ApiExceptionHandlerTest.java`

Test at minimum:

- `ResponseStatusException(404)` maps to `NOT_FOUND`
- `ResponseStatusException(401)` uses the safe generic message
- `BusinessException` preserves `code`
- `ClientException` and `ServiceUnavailableException` map to `503`
- generic exception maps to `500` and `INTERNAL_ERROR`
- validation extraction produces the expected `FieldError` list

## Phase 2: Refactor ReactiveApiExceptionHandler To Use Shared Support

### Files

- `service-web/src/main/java/org/budgetanalyzer/service/reactive/api/ReactiveApiExceptionHandler.java`
- `service-web/src/test/java/org/budgetanalyzer/service/reactive/api/ReactiveApiExceptionHandlerTest.java`

### Changes

Keep the existing `@ExceptionHandler` methods and class shape.

Refactor each common case to:

1. log
2. delegate to shared resolution
3. return `Mono.just(...)`

Examples:

- `handleNotFound(...)` delegates to `resolveCommonException(exception)`
- `handleInvalidRequest(...)` delegates to `resolveCommonException(exception)`
- `handleBusiness(...)` delegates to `resolveCommonException(exception)`
- `handleServiceUnavailable(...)` delegates to `resolveCommonException(exception)`
- `handleClientException(...)` delegates to `resolveCommonException(exception)`
- security-related handlers delegate to `resolveCommonException(exception)`
- `handleResponseStatusException(...)` delegates to `resolveCommonException(exception)`
- generic handler delegates to `resolveCommonException(exception)`

For validation:

- Keep the dedicated `WebExchangeBindException` handler
- Delegate field extraction to `resolveValidationFailure(exception.getBindingResult())`

### Simplicity Guardrail

Do not collapse all `@ExceptionHandler` methods into a single catch-all handler.
Keeping explicit methods is simpler, clearer, and safer for Spring resolution.

### Tests

Update existing tests rather than rewriting them wholesale.

Ensure `ReactiveApiExceptionHandlerTest` still covers:

- `400`, `401`, `403`, `404`, `422`, `503`, `500`
- `ResponseStatusException` type mapping
- validation field errors
- generic exception sanitization

Add at least one test that proves the shared resolver path is being exercised for a common
exception and one for `ResponseStatusException`.

## Phase 3: Add ReactiveErrorWebExceptionHandler

### Files

- `service-web/src/main/java/org/budgetanalyzer/service/reactive/api/ReactiveErrorWebExceptionHandler.java`
- `service-web/src/test/java/org/budgetanalyzer/service/reactive/api/ReactiveErrorWebExceptionHandlerTest.java`

### Changes

Add a new reactive fallback handler with these properties:

- implements `ErrorWebExceptionHandler`
- implements `Ordered`
- implements `ApiExceptionHandler`
- order `-1`
- writes `application/json`
- uses the shared resolver for common exceptions
- uses `resolveValidationFailure(...)` if the throwable is a `WebExchangeBindException`
- returns `Mono.error(throwable)` when the response is already committed

Inject `ObjectMapper` directly. Do not add a new writer abstraction.

Prefer a simple `handle(...)` flow:

1. guard for committed response
2. resolve throwable
3. serialize `ApiErrorResponse`
4. set status and `Content-Type`
5. write bytes to the response

### Registration

Register it so services can still override it cleanly.

Preferred simple approach:

- annotate the class with `@Component`
- add `@ConditionalOnMissingBean(ErrorWebExceptionHandler.class)`

Only change `ServiceWebAutoConfiguration` if testing shows that component scanning plus the class
level condition does not behave correctly.

### Simplicity Guardrail

Do not reimplement Boot error attributes, HTML rendering, or content negotiation.
This handler is JSON-only and exists only to enforce the shared API error contract.

### Tests

Add unit tests for:

- generic exception returns `500` with `INTERNAL_ERROR`
- `ResponseStatusException(404)` returns `404` with `NOT_FOUND`
- `AuthenticationException` returns `401` with safe message
- `AccessDeniedException` returns `403` with safe message
- committed response falls through with `Mono.error(...)`
- content type is `application/json`

If serialization-failure behavior is non-trivial during implementation, stop and ask before adding
extra abstraction.

## Phase 4: Refactor ServletApiExceptionHandler To Use Shared Support

### Files

- `service-web/src/main/java/org/budgetanalyzer/service/servlet/api/ServletApiExceptionHandler.java`
- `service-web/src/test/java/org/budgetanalyzer/service/servlet/api/ServletApiExceptionHandlerTest.java`

### Changes

Keep the existing servlet-specific handler methods and response shape.

Refactor shared cases to delegate to the common resolver:

- `InvalidRequestException`
- `ResourceNotFoundException`
- `BusinessException`
- `ClientException`
- `ServiceUnavailableException`
- security exceptions
- `ResponseStatusException`
- generic exception

Keep these servlet-specific cases local:

- `MethodArgumentNotValidException`
- `NoHandlerFoundException`
- `MethodArgumentTypeMismatchException`
- `MissingServletRequestPartException`
- `MissingServletRequestParameterException`

For `MethodArgumentNotValidException`, delegate field extraction through
`resolveValidationFailure(exception.getBindingResult())`.

### Simplicity Guardrail

Do not try to force servlet-only exceptions into the shared resolver just for symmetry.
Local handling is simpler and acceptable for stack-specific cases.

### Tests

Update existing servlet tests to confirm:

- shared-path mappings still return the same status and `ApiErrorType`
- servlet-specific mappings are unchanged
- validation still produces field errors
- `ResponseStatusException` status/type mapping still works
- safe messages for `401` and `403` are preserved

## Phase 5: Verify Auto-Configuration And Override Behavior

### Files

- `service-web/src/test/java/org/budgetanalyzer/service/reactive/api/ReactiveErrorWebExceptionHandlerAutoConfigurationTest.java`
- optionally `service-web/src/test/java/org/budgetanalyzer/service/integration/ServiceCommonReactiveAutoConfigurationIntegrationTest.java`

### Changes

Add context-runner coverage for bean registration.

Use `ReactiveWebApplicationContextRunner` with `ServiceWebAutoConfiguration` to verify:

- the new handler is registered in a reactive application
- it is not registered in a non-web application
- it backs off when a custom `ErrorWebExceptionHandler` bean is present

Add one end-to-end reactive integration test that proves filter-level exceptions now render the
shared contract. Keep it minimal:

- create a test `WebFilter` that throws a generic exception
- create a second path that throws `ResponseStatusException(404)`
- assert JSON `type` and HTTP status

Do not build a large mock application just for this. A single focused test class is enough.

## Phase 6: Documentation And Final Regression Pass

### Files

- `docs/error-handling.md`
- this plan file if implementation details changed materially during execution

### Changes

Update `docs/error-handling.md` so it explicitly documents that:

- controller exceptions are handled by shared servlet/reactive advice
- reactive filter-level exceptions also render `ApiErrorResponse` via
  `ReactiveErrorWebExceptionHandler`
- services may override the default reactive `ErrorWebExceptionHandler`

Keep the documentation short and concrete.

### Verification Commands

Run the required repo commands in sequence:

```bash
./gradlew clean spotlessApply
./gradlew clean build
```

If build time becomes an issue while iterating, it is fine to run focused tests before the full
build, but the full build must pass before the work is complete.

## Test Checklist

Before considering the work complete, verify all of the following:

- Shared resolver unit tests pass
- `ReactiveApiExceptionHandlerTest` passes
- `ServletApiExceptionHandlerTest` passes
- `ReactiveErrorWebExceptionHandlerTest` passes
- Reactive auto-configuration or context-runner tests pass
- Reactive filter-level integration test passes
- Full `./gradlew clean build` passes

## Exit Criteria

The work is complete when:

1. Common exception-to-contract mapping exists in one shared place.
2. Servlet and reactive controller advice both delegate to that shared mapping for common cases.
3. Reactive filter-level exceptions render the shared `ApiErrorResponse` JSON contract.
4. A consuming service can override the reactive `ErrorWebExceptionHandler`.
5. Documentation is updated.
6. Full test and build verification passes.

## Stop Conditions

Stop and ask for guidance if any of these become necessary:

- introducing a new abstraction layer beyond the helpers listed in Phase 1
- changing public exception semantics beyond the contract bug fix
- changing the `service-web` dependency model
- adding separate servlet filter-level error handling in the same change
- refactoring unrelated security or HTTP logging code to make the plan fit
