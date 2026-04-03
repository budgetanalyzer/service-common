package org.budgetanalyzer.service.api;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.server.ResponseStatusException;

import org.budgetanalyzer.service.exception.BusinessException;
import org.budgetanalyzer.service.exception.ClientException;
import org.budgetanalyzer.service.exception.InvalidRequestException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.exception.ServiceException;
import org.budgetanalyzer.service.exception.ServiceUnavailableException;

/**
 * Interface for exception handlers with shared response building logic.
 *
 * <p>Both servlet and reactive exception handlers implement this interface to reuse common error
 * response construction.
 */
public interface ApiExceptionHandler {

  /**
   * Resolved API error contract with HTTP status.
   *
   * @param statusCode HTTP status code
   * @param response API error response
   * @param headers HTTP headers that must be preserved on the response
   */
  record ResolvedError(HttpStatusCode statusCode, ApiErrorResponse response, HttpHeaders headers) {

    /**
     * Creates a resolved error with no additional HTTP headers.
     *
     * @param statusCode HTTP status code
     * @param response API error response
     */
    public ResolvedError(HttpStatusCode statusCode, ApiErrorResponse response) {
      this(statusCode, response, HttpHeaders.EMPTY);
    }

    /**
     * Creates a resolved error and normalizes headers to an immutable copy.
     *
     * @param statusCode HTTP status code
     * @param response API error response
     * @param headers HTTP headers that must be preserved on the response
     */
    public ResolvedError {
      headers = immutableHeaders(headers);
    }

    /**
     * Converts the resolved error to a response entity.
     *
     * @return response entity containing the resolved error payload
     */
    public ResponseEntity<ApiErrorResponse> toResponseEntity() {
      return ResponseEntity.status(statusCode).headers(headers).body(response);
    }

    private static HttpHeaders immutableHeaders(HttpHeaders headers) {
      if (headers == null || headers.isEmpty()) {
        return HttpHeaders.EMPTY;
      }

      var copiedHeaders = new HttpHeaders();
      copiedHeaders.putAll(headers);
      return HttpHeaders.readOnlyHttpHeaders(copiedHeaders);
    }
  }

  /**
   * Builds a validation error response.
   *
   * @param fieldErrors list of field validation errors
   * @return API error response
   */
  default ApiErrorResponse buildValidationError(List<FieldError> fieldErrors) {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.VALIDATION_ERROR)
        .message("Validation failed for " + fieldErrors.size() + " field(s)")
        .fieldErrors(fieldErrors)
        .build();
  }

  /**
   * Builds a not found error response.
   *
   * @param exception the exception
   * @return API error response
   */
  default ApiErrorResponse buildNotFoundError(ResourceNotFoundException exception) {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.NOT_FOUND)
        .message(exception.getMessage())
        .build();
  }

  /**
   * Builds an invalid request error response.
   *
   * @param exception the exception
   * @return API error response
   */
  default ApiErrorResponse buildInvalidRequestError(InvalidRequestException exception) {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.INVALID_REQUEST)
        .message(exception.getMessage())
        .build();
  }

  /**
   * Builds a business error response with error code.
   *
   * <p>If the exception contains field-level errors (e.g., from batch validation), they are
   * included in the response.
   *
   * @param exception the exception
   * @return API error response
   */
  default ApiErrorResponse buildBusinessError(BusinessException exception) {
    var builder =
        ApiErrorResponse.builder()
            .type(ApiErrorType.APPLICATION_ERROR)
            .code(exception.getCode())
            .message(exception.getMessage());

    if (exception.hasFieldErrors()) {
      builder.fieldErrors(exception.getFieldErrors());
    }

    return builder.build();
  }

  /**
   * Builds a service unavailable error response.
   *
   * @param exception the exception
   * @return API error response
   */
  default ApiErrorResponse buildServiceUnavailableError(ServiceException exception) {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.SERVICE_UNAVAILABLE)
        .message(exception.getMessage())
        .build();
  }

  /**
   * Builds a generic internal error response.
   *
   * @param exception the exception
   * @return API error response
   */
  default ApiErrorResponse buildInternalError(Exception exception) {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.INTERNAL_ERROR)
        .message("An unexpected error occurred")
        .build();
  }

  /**
   * Builds an unauthorized error response for authentication failures.
   *
   * <p>Security: uses a hardcoded generic message to prevent leaking authentication mechanism
   * details (e.g., which header was missing, why a token was rejected). Do not replace with
   * exception-specific messages. See also {@link #messageForStatus} which enforces the same policy
   * for {@code ResponseStatusException} with 401 status.
   *
   * @return API error response with UNAUTHORIZED type
   */
  default ApiErrorResponse buildUnauthorizedError() {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.UNAUTHORIZED)
        .message("Authentication required")
        .build();
  }

  /**
   * Builds a permission denied error response for authorization failures.
   *
   * <p>Security: uses a hardcoded generic message to prevent leaking authorization details (e.g.,
   * which permission or role was required). Do not replace with exception-specific messages. See
   * also {@link #messageForStatus} which enforces the same policy for {@code
   * ResponseStatusException} with 403 status.
   *
   * @return API error response with FORBIDDEN type
   */
  default ApiErrorResponse buildPermissionDeniedError() {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.FORBIDDEN)
        .message("You do not have permission to perform this action")
        .build();
  }

  /**
   * Converts a resolved error to a response entity.
   *
   * @param resolvedError resolved error
   * @return response entity containing the resolved error payload
   */
  default ResponseEntity<ApiErrorResponse> toResponseEntity(ResolvedError resolvedError) {
    return resolvedError.toResponseEntity();
  }

  /**
   * Resolves common shared exceptions to HTTP status and API error response.
   *
   * @param throwable the exception to resolve
   * @return resolved API error
   */
  default ResolvedError resolveCommonException(Throwable throwable) {
    if (throwable instanceof ResourceNotFoundException exception) {
      return new ResolvedError(HttpStatus.NOT_FOUND, buildNotFoundError(exception));
    }
    if (throwable instanceof InvalidRequestException exception) {
      return new ResolvedError(HttpStatus.BAD_REQUEST, buildInvalidRequestError(exception));
    }
    if (throwable instanceof BusinessException exception) {
      return new ResolvedError(HttpStatus.UNPROCESSABLE_ENTITY, buildBusinessError(exception));
    }
    if (throwable instanceof ClientException exception) {
      return new ResolvedError(
          HttpStatus.SERVICE_UNAVAILABLE, buildServiceUnavailableError(exception));
    }
    if (throwable instanceof ServiceUnavailableException exception) {
      return new ResolvedError(
          HttpStatus.SERVICE_UNAVAILABLE, buildServiceUnavailableError(exception));
    }
    if (throwable instanceof AccessDeniedException
        || throwable instanceof AuthorizationDeniedException) {
      return new ResolvedError(HttpStatus.FORBIDDEN, buildPermissionDeniedError());
    }
    if (throwable instanceof AuthenticationException) {
      return new ResolvedError(HttpStatus.UNAUTHORIZED, buildUnauthorizedError());
    }
    if (throwable instanceof ResponseStatusException exception) {
      return resolveResponseStatus(exception);
    }
    if (throwable instanceof Exception exception) {
      return new ResolvedError(HttpStatus.INTERNAL_SERVER_ERROR, buildInternalError(exception));
    }
    return new ResolvedError(
        HttpStatus.INTERNAL_SERVER_ERROR, buildInternalError(new Exception(throwable)));
  }

  /**
   * Resolves validation failures to HTTP 400 with standardized field errors.
   *
   * @param bindingResult the binding result containing validation failures
   * @return resolved validation error
   */
  default ResolvedError resolveValidationFailure(BindingResult bindingResult) {
    return new ResolvedError(
        HttpStatus.BAD_REQUEST, buildValidationError(extractFieldErrors(bindingResult)));
  }

  /**
   * Extracts API field errors from a Spring binding result.
   *
   * @param bindingResult the binding result containing validation failures
   * @return extracted API field errors
   */
  default List<FieldError> extractFieldErrors(BindingResult bindingResult) {
    return bindingResult.getFieldErrors().stream()
        .map(
            springFieldError ->
                FieldError.of(
                    springFieldError.getField(),
                    springFieldError.getDefaultMessage(),
                    springFieldError.getRejectedValue()))
        .toList();
  }

  /**
   * Resolves a response status exception to the standardized API error contract.
   *
   * @param exception the response status exception
   * @return resolved API error
   */
  default ResolvedError resolveResponseStatus(ResponseStatusException exception) {
    var statusCode = exception.getStatusCode();
    var response =
        ApiErrorResponse.builder()
            .type(errorTypeForStatus(statusCode))
            .message(messageForStatus(statusCode, exception.getReason()))
            .build();
    return new ResolvedError(statusCode, response, exception.getHeaders());
  }

  /**
   * Maps an HTTP status code to the corresponding {@link ApiErrorType}.
   *
   * @param statusCode the HTTP status code
   * @return the matching error type
   */
  static ApiErrorType errorTypeForStatus(HttpStatusCode statusCode) {
    return switch (statusCode.value()) {
      case 401 -> ApiErrorType.UNAUTHORIZED;
      case 403 -> ApiErrorType.FORBIDDEN;
      case 404 -> ApiErrorType.NOT_FOUND;
      case 503 -> ApiErrorType.SERVICE_UNAVAILABLE;
      default -> {
        if (statusCode.is4xxClientError()) {
          yield ApiErrorType.INVALID_REQUEST;
        }
        yield ApiErrorType.INTERNAL_ERROR;
      }
    };
  }

  /**
   * Returns a safe client-facing message for a given HTTP status code and original reason.
   *
   * <p>Security: authentication (401) and authorization (403) errors use hardcoded generic messages
   * to avoid leaking security-sensitive details (e.g., which credential failed, which permission
   * was required). All other status codes pass through the original reason. This mirrors the policy
   * in {@link #buildUnauthorizedError()} and {@link #buildPermissionDeniedError()} — keep the
   * messages in sync if they ever change.
   *
   * @param statusCode the HTTP status code
   * @param reason the original exception reason (may be {@code null})
   * @return a safe message for the API response
   */
  static String messageForStatus(HttpStatusCode statusCode, String reason) {
    return switch (statusCode.value()) {
      case 401 -> "Authentication required";
      case 403 -> "You do not have permission to perform this action";
      default -> reason;
    };
  }
}
