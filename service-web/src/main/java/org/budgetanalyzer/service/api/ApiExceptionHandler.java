package org.budgetanalyzer.service.api;

import java.util.List;

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
   * @return API error response with FORBIDDEN type
   */
  default ApiErrorResponse buildPermissionDeniedError() {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.FORBIDDEN)
        .message("You do not have permission to perform this action")
        .build();
  }
}
