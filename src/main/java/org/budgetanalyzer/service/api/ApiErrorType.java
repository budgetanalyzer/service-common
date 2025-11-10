package org.budgetanalyzer.service.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeration of error types used in API error responses.
 *
 * <p>This enum categorizes errors into distinct types that help clients understand the nature of
 * the error and how to handle it. Each type corresponds to specific HTTP status codes and may have
 * associated fields in the {@link ApiErrorResponse}.
 *
 * <p>Error type to HTTP status mapping:
 *
 * <ul>
 *   <li>{@link #INVALID_REQUEST} - 400 Bad Request (malformed request data)
 *   <li>{@link #VALIDATION_ERROR} - 400 Bad Request (field validation failed, includes fieldErrors
 *       array)
 *   <li>{@link #NOT_FOUND} - 404 Not Found (resource doesn't exist)
 *   <li>{@link #APPLICATION_ERROR} - 422 Unprocessable Entity (business rule violation, includes
 *       code field)
 *   <li>{@link #SERVICE_UNAVAILABLE} - 503 Service Unavailable (downstream service failure)
 *   <li>{@link #INTERNAL_ERROR} - 500 Internal Server Error (unexpected server error)
 * </ul>
 *
 * @see ApiErrorResponse
 * @see DefaultApiExceptionHandler
 */
@Schema(description = "Error type categorization for API responses")
public enum ApiErrorType {

  /** Malformed request or bad syntax (HTTP 400). */
  @Schema(description = "Malformed request or bad syntax")
  INVALID_REQUEST,

  /** Field validation failed - check fieldErrors array (HTTP 400). */
  @Schema(description = "Field validation failed - check errors array")
  VALIDATION_ERROR,

  /** Requested resource does not exist (HTTP 404). */
  @Schema(description = "Requested resource does not exist")
  NOT_FOUND,

  /** Business rule violation - check code field (HTTP 422). */
  @Schema(description = "Business rule violation - check code field")
  APPLICATION_ERROR,

  /** Downstream service unavailable (HTTP 503). */
  @Schema(description = "Downstream service unavailable")
  SERVICE_UNAVAILABLE,

  /** Unexpected server error (HTTP 500). */
  @Schema(description = "Unexpected server error (HTTP 500)")
  INTERNAL_ERROR
}
