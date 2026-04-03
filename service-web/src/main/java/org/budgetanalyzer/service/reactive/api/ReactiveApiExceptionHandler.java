package org.budgetanalyzer.service.reactive.api;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.api.ApiErrorType;
import org.budgetanalyzer.service.api.ApiExceptionHandler;
import org.budgetanalyzer.service.exception.BusinessException;
import org.budgetanalyzer.service.exception.ClientException;
import org.budgetanalyzer.service.exception.InvalidRequestException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.exception.ServiceUnavailableException;

/**
 * Global exception handler for reactive (Spring WebFlux) applications.
 *
 * <p>Converts exceptions to standardized {@link ApiErrorResponse} objects.
 *
 * <p>This handler operates with {@link Ordered#LOWEST_PRECEDENCE}, meaning any service-specific
 * {@code @RestControllerAdvice} beans will take precedence and can override the default handling.
 *
 * <p>Exception to HTTP status mapping:
 *
 * <ul>
 *   <li>{@link InvalidRequestException} → 400 Bad Request
 *   <li>{@link ResourceNotFoundException} → 404 Not Found
 *   <li>{@link BusinessException} → 422 Unprocessable Entity (includes error code)
 *   <li>{@link ClientException} → 503 Service Unavailable
 *   <li>{@link ServiceUnavailableException} → 503 Service Unavailable
 *   <li>{@link WebExchangeBindException} → 400 Bad Request (validation errors)
 *   <li>{@link AccessDeniedException} → 403 Forbidden
 *   <li>{@link AuthorizationDeniedException} → 403 Forbidden
 *   <li>{@link AuthenticationException} → 401 Unauthorized
 *   <li>{@link ResponseStatusException} → preserves the original HTTP status code
 *   <li>Generic {@link Exception} → 500 Internal Server Error
 * </ul>
 *
 * @see ApiErrorResponse
 * @see ApiErrorType
 * @see ApiExceptionHandler
 */
@Component
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ReactiveApiExceptionHandler implements ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ReactiveApiExceptionHandler.class);

  /**
   * Handles {@link ResourceNotFoundException} and returns HTTP 404 Not Found.
   *
   * @param exception the exception thrown when a requested resource does not exist
   * @return Mono with standardized error response
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleNotFound(
      ResourceNotFoundException exception) {
    logException(ApiErrorType.NOT_FOUND, null, exception);
    return Mono.just(toResponseEntity(resolveCommonException(exception)));
  }

  /**
   * Handles {@link InvalidRequestException} and returns HTTP 400 Bad Request.
   *
   * @param exception the exception thrown when request data is malformed or invalid
   * @return Mono with standardized error response
   */
  @ExceptionHandler(InvalidRequestException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleInvalidRequest(
      InvalidRequestException exception) {
    logException(ApiErrorType.INVALID_REQUEST, null, exception);
    return Mono.just(toResponseEntity(resolveCommonException(exception)));
  }

  /**
   * Handles {@link BusinessException} and returns HTTP 422 Unprocessable Entity.
   *
   * @param exception the exception thrown when a business rule is violated
   * @return Mono with standardized error response
   */
  @ExceptionHandler(BusinessException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleBusiness(BusinessException exception) {
    logException(ApiErrorType.APPLICATION_ERROR, exception.getCode(), exception);
    return Mono.just(toResponseEntity(resolveCommonException(exception)));
  }

  /**
   * Handles {@link WebExchangeBindException} and returns HTTP 400 Bad Request.
   *
   * <p>This is the reactive equivalent of MethodArgumentNotValidException, thrown when Spring
   * validation fails on a {@code @Valid} annotated request body.
   *
   * @param exception the exception thrown when request body validation fails
   * @return Mono with standardized error response
   */
  @ExceptionHandler(WebExchangeBindException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleValidation(
      WebExchangeBindException exception) {
    var resolvedError = resolveValidationFailure(exception.getBindingResult());
    var fieldErrors = resolvedError.response().getFieldErrors();

    log.warn(
        "Handled exception type: VALIDATION_ERROR exception: {} field count: {} message: {}",
        exception.getClass(),
        fieldErrors.size(),
        resolvedError.response().getMessage(),
        exception);

    return Mono.just(toResponseEntity(resolvedError));
  }

  /**
   * Handles {@link ServiceUnavailableException} and returns HTTP 503 Service Unavailable.
   *
   * @param exception the exception thrown when a service dependency is unavailable
   * @return Mono with standardized error response
   */
  @ExceptionHandler(ServiceUnavailableException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleServiceUnavailable(
      ServiceUnavailableException exception) {
    logException(ApiErrorType.SERVICE_UNAVAILABLE, null, exception);
    return Mono.just(toResponseEntity(resolveCommonException(exception)));
  }

  /**
   * Handles {@link ClientException} and returns HTTP 503 Service Unavailable.
   *
   * @param exception the exception thrown when a downstream client service fails
   * @return Mono with standardized error response
   */
  @ExceptionHandler(ClientException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleClientException(ClientException exception) {
    logException(ApiErrorType.SERVICE_UNAVAILABLE, null, exception);
    return Mono.just(toResponseEntity(resolveCommonException(exception)));
  }

  /**
   * Handles {@link AccessDeniedException} and returns HTTP 403 Forbidden.
   *
   * @param exception the access denied exception from Spring Security
   * @return Mono with standardized error response with FORBIDDEN type
   */
  @ExceptionHandler(AccessDeniedException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleAccessDenied(
      AccessDeniedException exception) {
    log.warn(
        "Handled security exception: {} message: {}",
        exception.getClass().getSimpleName(),
        exception.getMessage());
    return Mono.just(toResponseEntity(resolveCommonException(exception)));
  }

  /**
   * Handles {@link AuthorizationDeniedException} and returns HTTP 403 Forbidden.
   *
   * @param exception the authorization denied exception from Spring Security
   * @return Mono with standardized error response with FORBIDDEN type
   */
  @ExceptionHandler(AuthorizationDeniedException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleAuthorizationDenied(
      AuthorizationDeniedException exception) {
    log.warn(
        "Handled security exception: {} message: {}",
        exception.getClass().getSimpleName(),
        exception.getMessage());
    return Mono.just(toResponseEntity(resolveCommonException(exception)));
  }

  /**
   * Handles {@link AuthenticationException} and returns HTTP 401 Unauthorized.
   *
   * @param exception the authentication exception from Spring Security
   * @return Mono with standardized error response with UNAUTHORIZED type
   */
  @ExceptionHandler(AuthenticationException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleAuthentication(
      AuthenticationException exception) {
    log.warn(
        "Handled security exception: {} message: {}",
        exception.getClass().getSimpleName(),
        exception.getMessage());
    return Mono.just(toResponseEntity(resolveCommonException(exception)));
  }

  /**
   * Handles {@link ResponseStatusException} and preserves the original HTTP status code.
   *
   * <p>Without this handler, {@code ResponseStatusException} falls through to the generic {@link
   * Exception} handler, which incorrectly returns HTTP 500 for all status codes.
   *
   * @param exception the response status exception with an explicit HTTP status code
   * @return Mono with standardized error response preserving the original status code
   */
  @ExceptionHandler(ResponseStatusException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleResponseStatusException(
      ResponseStatusException exception) {
    var statusCode = exception.getStatusCode();
    log.warn(
        "Handled ResponseStatusException: status={} reason={}",
        statusCode.value(),
        exception.getReason());
    return Mono.just(toResponseEntity(resolveCommonException(exception)));
  }

  /**
   * Handles all unhandled {@link Exception} types and returns HTTP 500 Internal Server Error.
   *
   * <p>This is the catch-all handler for any exceptions not explicitly handled by other methods.
   *
   * @param exception the unhandled exception
   * @return Mono with standardized error response
   */
  @ExceptionHandler(Exception.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleGenericException(Exception exception) {
    logException(ApiErrorType.INTERNAL_ERROR, null, exception);
    return Mono.just(toResponseEntity(resolveCommonException(exception)));
  }

  /**
   * Logs exception details with root cause information.
   *
   * @param type error type
   * @param code error code (optional)
   * @param throwable the exception
   */
  private void logException(ApiErrorType type, String code, Throwable throwable) {
    var message = throwable.getMessage();
    var rootCause = ExceptionUtils.getRootCause(throwable);
    if (rootCause != null) {
      log.warn(
          "Handled exception type: {} code: {} exception: {} root cause: {} message: {}",
          type,
          code,
          throwable.getClass(),
          rootCause.getClass(),
          message,
          throwable);
    } else {
      log.warn(
          "Handled exception type: {} code: {} exception: {} message: {}",
          type,
          code,
          throwable.getClass(),
          message,
          throwable);
    }
  }
}
