package org.budgetanalyzer.service.api;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import org.budgetanalyzer.service.exception.BusinessException;
import org.budgetanalyzer.service.exception.ClientException;
import org.budgetanalyzer.service.exception.InvalidRequestException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.exception.ServiceUnavailableException;

/**
 * Global exception handler that converts exceptions to standardized {@link ApiErrorResponse}
 * objects.
 *
 * <p>This handler operates with {@link Ordered#LOWEST_PRECEDENCE}, meaning any service-specific
 * {@code @RestControllerAdvice} beans will take precedence and can override the default handling.
 * This design allows microservices to customize error handling while maintaining consistent default
 * behavior.
 *
 * <p>Exception to HTTP status mapping:
 *
 * <ul>
 *   <li>{@link InvalidRequestException} → 400 Bad Request
 *   <li>{@link ResourceNotFoundException} → 404 Not Found
 *   <li>{@link BusinessException} → 422 Unprocessable Entity (includes error code)
 *   <li>{@link ClientException} → 503 Service Unavailable
 *   <li>{@link ServiceUnavailableException} → 503 Service Unavailable
 *   <li>{@link MethodArgumentTypeMismatchException} → 400 Bad Request
 *   <li>{@link MissingServletRequestPartException} → 400 Bad Request
 *   <li>Generic {@link Exception} → 500 Internal Server Error
 * </ul>
 *
 * <p>All exceptions are logged with WARN level, including root cause information when available.
 *
 * <p>This handler is auto-configured and will be automatically discovered by Spring Boot component
 * scanning when the service-common library is included as a dependency.
 *
 * @see ApiErrorResponse
 * @see ApiErrorType
 */
@AutoConfiguration
@ConditionalOnWebApplication
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(DefaultApiExceptionHandler.class);

  /**
   * Handles {@link InvalidRequestException} and returns HTTP 400 Bad Request.
   *
   * @param exception the exception thrown when request data is malformed or invalid
   * @param request the web request context
   * @return standardized error response with INVALID_REQUEST type
   */
  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handle(InvalidRequestException exception, WebRequest request) {
    return handleApiException(ApiErrorType.INVALID_REQUEST, exception);
  }

  /**
   * Handles {@link MethodArgumentNotValidException} and returns HTTP 400 Bad Request.
   *
   * <p>This exception is thrown when Spring validation fails on a {@code @Valid} annotated request
   * body. The response includes field-level error details for each validation failure.
   *
   * @param exception the exception thrown when request body validation fails
   * @param request the web request context
   * @return standardized error response with VALIDATION_ERROR type and field errors
   */
  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handle(MethodArgumentNotValidException exception, WebRequest request) {
    var fieldErrors =
        exception.getBindingResult().getAllErrors().stream()
            .filter(error -> error instanceof org.springframework.validation.FieldError)
            .map(
                error -> {
                  var springFieldError = (org.springframework.validation.FieldError) error;
                  return FieldError.of(
                      springFieldError.getField(),
                      error.getDefaultMessage(),
                      springFieldError.getRejectedValue());
                })
            .toList();

    var message =
        "Validation failed for "
            + fieldErrors.size()
            + " field"
            + (fieldErrors.size() != 1 ? "s" : "");

    log.warn(
        "Handled exception type: VALIDATION_ERROR exception: {} field count: {} message: {}",
        exception.getClass(),
        fieldErrors.size(),
        message,
        exception);

    return ApiErrorResponse.builder()
        .type(ApiErrorType.VALIDATION_ERROR)
        .message(message)
        .fieldErrors(fieldErrors)
        .build();
  }

  /**
   * Handles {@link ResourceNotFoundException} and returns HTTP 404 Not Found.
   *
   * @param exception the exception thrown when a requested resource does not exist
   * @param request the web request context
   * @return standardized error response with NOT_FOUND type
   */
  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.NOT_FOUND)
  public ApiErrorResponse handle(ResourceNotFoundException exception, WebRequest request) {
    return handleApiException(ApiErrorType.NOT_FOUND, exception);
  }

  /**
   * Handles {@link BusinessException} and returns HTTP 422 Unprocessable Entity.
   *
   * <p>This handler includes the application-specific error code in the response.
   *
   * @param exception the exception thrown when a business rule is violated
   * @param request the web request context
   * @return standardized error response with APPLICATION_ERROR type and error code
   */
  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
  public ApiErrorResponse handle(BusinessException exception, WebRequest request) {
    return handleApiException(ApiErrorType.APPLICATION_ERROR, exception.getCode(), exception);
  }

  /**
   * Handles {@link ClientException} and returns HTTP 503 Service Unavailable.
   *
   * @param exception the exception thrown when a downstream client service fails
   * @param request the web request context
   * @return standardized error response with SERVICE_UNAVAILABLE type
   */
  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
  public ApiErrorResponse handle(ClientException exception, WebRequest request) {
    return handleApiException(ApiErrorType.SERVICE_UNAVAILABLE, exception);
  }

  /**
   * Handles {@link ServiceUnavailableException} and returns HTTP 503 Service Unavailable.
   *
   * @param exception the exception thrown when a service dependency is unavailable
   * @param request the web request context
   * @return standardized error response with SERVICE_UNAVAILABLE type
   */
  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
  public ApiErrorResponse handle(ServiceUnavailableException exception, WebRequest request) {
    return handleApiException(ApiErrorType.SERVICE_UNAVAILABLE, exception);
  }

  /**
   * Handles {@link MethodArgumentTypeMismatchException} and returns HTTP 400 Bad Request.
   *
   * <p>This exception occurs when a request parameter cannot be converted to the expected type
   * (e.g., passing "abc" for an integer parameter).
   *
   * @param exception the exception thrown when method argument type conversion fails
   * @param request the web request context
   * @return standardized error response with INVALID_REQUEST type
   */
  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handle(
      MethodArgumentTypeMismatchException exception, WebRequest request) {
    return handleApiException(ApiErrorType.INVALID_REQUEST, exception);
  }

  /**
   * Handles {@link MissingServletRequestPartException} and returns HTTP 400 Bad Request.
   *
   * <p>This exception occurs when a required multipart file or parameter is missing from the
   * request.
   *
   * @param exception the exception thrown when a required multipart request part is missing
   * @param request the web request context
   * @return standardized error response with INVALID_REQUEST type
   */
  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handle(MissingServletRequestPartException exception, WebRequest request) {
    return handleApiException(ApiErrorType.INVALID_REQUEST, exception);
  }

  /**
   * Handles {@link MissingServletRequestParameterException} and returns HTTP 400 Bad Request.
   *
   * <p>This exception occurs when a required request parameter (e.g., a query parameter or form
   * parameter) is missing from the HTTP request. This is commonly triggered by
   * {@code @RequestParam} annotations without {@code required=false}.
   *
   * @param exception the exception thrown when a required request parameter is missing
   * @param request the web request context
   * @return standardized error response with INVALID_REQUEST type
   */
  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handle(
      MissingServletRequestParameterException exception, WebRequest request) {
    return handleApiException(ApiErrorType.INVALID_REQUEST, exception);
  }

  /**
   * Handles all unhandled {@link Exception} types and returns HTTP 500 Internal Server Error.
   *
   * <p>This is the catch-all handler for any exceptions not explicitly handled by other methods. It
   * ensures all exceptions result in a standardized error response rather than exposing internal
   * stack traces to clients.
   *
   * @param exception the unhandled exception
   * @param request the web request context
   * @return standardized error response with INTERNAL_ERROR type
   */
  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiErrorResponse handle(Exception exception, WebRequest request) {
    return handleApiException(ApiErrorType.INTERNAL_ERROR, exception);
  }

  private ApiErrorResponse handleApiException(ApiErrorType type, Throwable throwable) {
    return handleApiException(type, null, throwable);
  }

  private ApiErrorResponse handleApiException(ApiErrorType type, String code, Throwable throwable) {
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

    return ApiErrorResponse.builder().type(type).message(message).code(code).build();
  }
}
