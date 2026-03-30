package org.budgetanalyzer.service.api;

import java.util.List;

import org.springframework.http.HttpStatusCode;

import org.budgetanalyzer.service.exception.BusinessException;
import org.budgetanalyzer.service.exception.InvalidRequestException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.exception.ServiceException;

/**
 * Interface for exception handlers with shared response building logic.
 *
 * <p>Both servlet and reactive exception handlers implement this interface to reuse common error
 * response construction.
 */
public interface ApiExceptionHandler {

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
