package org.budgetanalyzer.service.api;

import static org.assertj.core.api.Assertions.assertThat;

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

    assertThat(resolvedError.statusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resolvedError.response().getType()).isEqualTo(ApiErrorType.NOT_FOUND);
    assertThat(resolvedError.response().getMessage()).isEqualTo("Resource not found");
  }

  @Test
  @DisplayName("Should resolve ResponseStatusException 401 with safe generic message")
  void shouldResolveResponseStatusException401WithSafeMessage() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key expired"));

    assertThat(resolvedError.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resolvedError.response().getType()).isEqualTo(ApiErrorType.UNAUTHORIZED);
    assertThat(resolvedError.response().getMessage()).isEqualTo("Authentication required");
  }

  @Test
  @DisplayName("Should preserve ResponseStatusException headers")
  void shouldPreserveResponseStatusExceptionHeaders() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(
            new MethodNotAllowedException(
                HttpMethod.POST, List.of(HttpMethod.GET, HttpMethod.PUT)));

    assertThat(resolvedError.statusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(resolvedError.headers().getAllow())
        .isEqualTo(Set.of(HttpMethod.GET, HttpMethod.PUT));
  }

  @Test
  @DisplayName("Should preserve BusinessException code")
  void shouldPreserveBusinessExceptionCode() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(
            new BusinessException("Budget exceeded", "BUDGET_EXCEEDED"));

    assertThat(resolvedError.statusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(resolvedError.response().getType()).isEqualTo(ApiErrorType.APPLICATION_ERROR);
    assertThat(resolvedError.response().getCode()).isEqualTo("BUDGET_EXCEEDED");
  }

  @Test
  @DisplayName("Should map ClientException to service unavailable")
  void shouldMapClientExceptionToServiceUnavailable() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(new ClientException("Downstream failure"));

    assertThat(resolvedError.statusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(resolvedError.response().getType()).isEqualTo(ApiErrorType.SERVICE_UNAVAILABLE);
    assertThat(resolvedError.response().getMessage()).isEqualTo("Downstream failure");
  }

  @Test
  @DisplayName("Should map ServiceUnavailableException to service unavailable")
  void shouldMapServiceUnavailableExceptionToServiceUnavailable() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(
            new ServiceUnavailableException("Database unavailable"));

    assertThat(resolvedError.statusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(resolvedError.response().getType()).isEqualTo(ApiErrorType.SERVICE_UNAVAILABLE);
    assertThat(resolvedError.response().getMessage()).isEqualTo("Database unavailable");
  }

  @Test
  @DisplayName("Should map generic exception to internal error")
  void shouldMapGenericExceptionToInternalError() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(new IllegalStateException("Unexpected"));

    assertThat(resolvedError.statusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(resolvedError.response().getType()).isEqualTo(ApiErrorType.INTERNAL_ERROR);
    assertThat(resolvedError.response().getMessage()).isEqualTo("An unexpected error occurred");
  }

  @Test
  @DisplayName("Should map authentication exception to safe unauthorized response")
  void shouldMapAuthenticationExceptionToUnauthorized() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(new BadCredentialsException("Bad credentials"));

    assertThat(resolvedError.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resolvedError.response().getType()).isEqualTo(ApiErrorType.UNAUTHORIZED);
    assertThat(resolvedError.response().getMessage()).isEqualTo("Authentication required");
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

    assertThat(resolvedError.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resolvedError.response().getType()).isEqualTo(ApiErrorType.VALIDATION_ERROR);
    assertThat(resolvedError.response().getFieldErrors()).isNotNull();
    assertThat(resolvedError.response().getFieldErrors().size()).isEqualTo(2);
    assertThat(resolvedError.response().getFieldErrors().get(0).getField()).isEqualTo("name");
    assertThat(resolvedError.response().getFieldErrors().get(0).getMessage())
        .isEqualTo("must not be blank");
    assertThat(resolvedError.response().getFieldErrors().get(0).getRejectedValue()).isEqualTo("");
    assertThat(resolvedError.response().getFieldErrors().get(1).getField()).isEqualTo("age");
    assertThat(resolvedError.response().getFieldErrors().get(1).getMessage())
        .isEqualTo("must be positive");
    assertThat(resolvedError.response().getFieldErrors().get(1).getRejectedValue()).isEqualTo(-1);
  }

  @Test
  @DisplayName("Should convert resolved error to response entity")
  void shouldConvertResolvedErrorToResponseEntity() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Missing resource"));

    var responseEntity = apiExceptionHandler.toResponseEntity(resolvedError);

    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(responseEntity.getBody()).isNotNull();
    assertThat(responseEntity.getBody().getType()).isEqualTo(ApiErrorType.NOT_FOUND);
    assertThat(responseEntity.getBody().getMessage()).isEqualTo("Missing resource");
    assertThat(responseEntity.getBody().getCode()).isNull();
  }

  @Test
  @DisplayName("Should convert resolved error to response entity with headers")
  void shouldConvertResolvedErrorToResponseEntityWithHeaders() {
    var resolvedError =
        apiExceptionHandler.resolveCommonException(
            new MethodNotAllowedException(
                HttpMethod.POST, List.of(HttpMethod.GET, HttpMethod.PUT)));

    var responseEntity = apiExceptionHandler.toResponseEntity(resolvedError);

    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(responseEntity.getHeaders().getAllow())
        .isEqualTo(Set.of(HttpMethod.GET, HttpMethod.PUT));
    assertThat(responseEntity.getBody()).isNotNull();
    assertThat(responseEntity.getBody().getType()).isEqualTo(ApiErrorType.INVALID_REQUEST);
  }

  private record TestPayload(String name, int age) {}
}
