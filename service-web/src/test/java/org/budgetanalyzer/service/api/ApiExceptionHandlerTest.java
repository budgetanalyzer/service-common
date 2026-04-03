package org.budgetanalyzer.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ResponseStatusException;

import org.budgetanalyzer.service.exception.BusinessException;
import org.budgetanalyzer.service.exception.ClientException;
import org.budgetanalyzer.service.exception.ServiceUnavailableException;

/** Unit tests for shared {@link ApiExceptionHandler} resolution helpers. */
@DisplayName("ApiExceptionHandler Tests")
class ApiExceptionHandlerTest {

  private final ApiExceptionHandler apiExceptionHandler = new ApiExceptionHandler() {};

  @Test
  @DisplayName("Should resolve ResponseStatusException 404 as NOT_FOUND")
  void shouldResolveResponseStatusException404() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));

    assertEquals(HttpStatus.NOT_FOUND, resolvedError.statusCode());
    assertEquals(ApiErrorType.NOT_FOUND, resolvedError.response().getType());
    assertEquals("Resource not found", resolvedError.response().getMessage());
  }

  @Test
  @DisplayName("Should resolve ResponseStatusException 401 with safe generic message")
  void shouldResolveResponseStatusException401WithSafeMessage() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key expired"));

    assertEquals(HttpStatus.UNAUTHORIZED, resolvedError.statusCode());
    assertEquals(ApiErrorType.UNAUTHORIZED, resolvedError.response().getType());
    assertEquals("Authentication required", resolvedError.response().getMessage());
  }

  @Test
  @DisplayName("Should preserve ResponseStatusException headers")
  void shouldPreserveResponseStatusExceptionHeaders() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(
            new MethodNotAllowedException(
                HttpMethod.POST, List.of(HttpMethod.GET, HttpMethod.PUT)));

    assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resolvedError.statusCode());
    assertEquals(Set.of(HttpMethod.GET, HttpMethod.PUT), resolvedError.headers().getAllow());
  }

  @Test
  @DisplayName("Should preserve BusinessException code")
  void shouldPreserveBusinessExceptionCode() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(
            new BusinessException("Budget exceeded", "BUDGET_EXCEEDED"));

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resolvedError.statusCode());
    assertEquals(ApiErrorType.APPLICATION_ERROR, resolvedError.response().getType());
    assertEquals("BUDGET_EXCEEDED", resolvedError.response().getCode());
  }

  @Test
  @DisplayName("Should map ClientException to service unavailable")
  void shouldMapClientExceptionToServiceUnavailable() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(new ClientException("Downstream failure"));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resolvedError.statusCode());
    assertEquals(ApiErrorType.SERVICE_UNAVAILABLE, resolvedError.response().getType());
    assertEquals("Downstream failure", resolvedError.response().getMessage());
  }

  @Test
  @DisplayName("Should map ServiceUnavailableException to service unavailable")
  void shouldMapServiceUnavailableExceptionToServiceUnavailable() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(
            new ServiceUnavailableException("Database unavailable"));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resolvedError.statusCode());
    assertEquals(ApiErrorType.SERVICE_UNAVAILABLE, resolvedError.response().getType());
    assertEquals("Database unavailable", resolvedError.response().getMessage());
  }

  @Test
  @DisplayName("Should map generic exception to internal error")
  void shouldMapGenericExceptionToInternalError() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(new IllegalStateException("Unexpected"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resolvedError.statusCode());
    assertEquals(ApiErrorType.INTERNAL_ERROR, resolvedError.response().getType());
    assertEquals("An unexpected error occurred", resolvedError.response().getMessage());
  }

  @Test
  @DisplayName("Should map authentication exception to safe unauthorized response")
  void shouldMapAuthenticationExceptionToUnauthorized() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(new BadCredentialsException("Bad credentials"));

    assertEquals(HttpStatus.UNAUTHORIZED, resolvedError.statusCode());
    assertEquals(ApiErrorType.UNAUTHORIZED, resolvedError.response().getType());
    assertEquals("Authentication required", resolvedError.response().getMessage());
  }

  @Test
  @DisplayName("Should extract field errors from binding result")
  void shouldExtractFieldErrorsFromBindingResult() {
    var bindingResult = new BeanPropertyBindingResult(new TestPayload("", -1), "testPayload");
    bindingResult.addError(
        new org.springframework.validation.FieldError(
            "testPayload", "name", "", false, null, null, "must not be blank"));
    bindingResult.addError(
        new org.springframework.validation.FieldError(
            "testPayload", "age", -1, false, null, null, "must be positive"));

    var resolvedError = apiExceptionHandler.resolveValidationFailure(bindingResult);

    assertEquals(HttpStatus.BAD_REQUEST, resolvedError.statusCode());
    assertEquals(ApiErrorType.VALIDATION_ERROR, resolvedError.response().getType());
    assertNotNull(resolvedError.response().getFieldErrors());
    assertEquals(2, resolvedError.response().getFieldErrors().size());
    assertEquals("name", resolvedError.response().getFieldErrors().get(0).getField());
    assertEquals(
        "must not be blank", resolvedError.response().getFieldErrors().get(0).getMessage());
    assertEquals("", resolvedError.response().getFieldErrors().get(0).getRejectedValue());
    assertEquals("age", resolvedError.response().getFieldErrors().get(1).getField());
    assertEquals("must be positive", resolvedError.response().getFieldErrors().get(1).getMessage());
    assertEquals(-1, resolvedError.response().getFieldErrors().get(1).getRejectedValue());
  }

  @Test
  @DisplayName("Should convert resolved error to response entity")
  void shouldConvertResolvedErrorToResponseEntity() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Missing resource"));

    var responseEntity = apiExceptionHandler.toResponseEntity(resolvedError);

    assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
    assertNotNull(responseEntity.getBody());
    assertEquals(ApiErrorType.NOT_FOUND, responseEntity.getBody().getType());
    assertEquals("Missing resource", responseEntity.getBody().getMessage());
    assertNull(responseEntity.getBody().getCode());
  }

  @Test
  @DisplayName("Should convert resolved error to response entity with headers")
  void shouldConvertResolvedErrorToResponseEntityWithHeaders() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(
            new MethodNotAllowedException(
                HttpMethod.POST, List.of(HttpMethod.GET, HttpMethod.PUT)));

    var responseEntity = apiExceptionHandler.toResponseEntity(resolvedError);

    assertEquals(HttpStatus.METHOD_NOT_ALLOWED, responseEntity.getStatusCode());
    assertEquals(Set.of(HttpMethod.GET, HttpMethod.PUT), responseEntity.getHeaders().getAllow());
    assertNotNull(responseEntity.getBody());
    assertEquals(ApiErrorType.INVALID_REQUEST, responseEntity.getBody().getType());
  }

  private record TestPayload(String name, int age) {}
}
