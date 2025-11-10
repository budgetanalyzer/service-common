package org.budgetanalyzer.service.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Standard API error response format used across all microservices.
 *
 * <p>This class provides a consistent error response structure that includes:
 *
 * <ul>
 *   <li>{@code type} - Error type categorization (INVALID_REQUEST, VALIDATION_ERROR, NOT_FOUND,
 *       APPLICATION_ERROR, SERVICE_UNAVAILABLE, INTERNAL_ERROR)
 *   <li>{@code message} - Human-readable error description
 *   <li>{@code code} - Optional machine-readable error code (for APPLICATION_ERROR type)
 *   <li>{@code fieldErrors} - Optional list of field-level validation errors (for VALIDATION_ERROR
 *       type)
 * </ul>
 *
 * <p>Example JSON representation:
 *
 * <pre>
 * {
 *   "type": "VALIDATION_ERROR",
 *   "message": "One or more fields have validation errors",
 *   "fieldErrors": [
 *     {
 *       "field": "amount",
 *       "rejectedValue": "-100",
 *       "message": "Amount must be positive"
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>Use the builder pattern to construct instances:
 *
 * <pre>
 * ApiErrorResponse response = ApiErrorResponse.builder()
 *     .type(ApiErrorType.APPLICATION_ERROR)
 *     .message("CSV format not supported")
 *     .code("CSV_PARSING_ERROR")
 *     .build();
 * </pre>
 *
 * @see ApiErrorType
 * @see FieldError
 * @see DefaultApiExceptionHandler
 */
@Schema(description = "Standard API error response format")
public class ApiErrorResponse {

  @Schema(
      description = "Error type indicating the category and structure of the error",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example = "APPLICATION_ERROR")
  private ApiErrorType type;

  @Schema(
      description = "Human-readable message describing the error",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example = "CSV format: fake-bank not supported")
  private String message;

  @Schema(
      description =
          "Machine-readable error code for specific application errors "
              + "(required for APPLICATION_ERROR type)",
      example = "CSV_PARSING_ERROR")
  private String code;

  @Schema(
      description = "List of field-level validation errors (populated for VALIDATION_ERROR type)")
  private List<FieldError> fieldErrors;

  // Private constructor for builder
  private ApiErrorResponse() {}

  /**
   * Gets the error type.
   *
   * @return the error type
   */
  public ApiErrorType getType() {
    return type;
  }

  /**
   * Gets the human-readable error message.
   *
   * @return the error message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Gets the machine-readable error code.
   *
   * @return the error code, or null if not applicable
   */
  public String getCode() {
    return code;
  }

  /**
   * Gets the list of field-level validation errors.
   *
   * @return the field errors, or null if not a validation error
   */
  public List<FieldError> getFieldErrors() {
    return fieldErrors;
  }

  /**
   * Creates a new builder for constructing ApiErrorResponse instances.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for constructing ApiErrorResponse instances with a fluent API. */
  public static class Builder {
    private final ApiErrorResponse response = new ApiErrorResponse();

    /**
     * Sets the error type.
     *
     * @param type the error type to set
     * @return this builder instance
     */
    public Builder type(ApiErrorType type) {
      response.type = type;
      return this;
    }

    /**
     * Sets the error message.
     *
     * @param message the error message to set
     * @return this builder instance
     */
    public Builder message(String message) {
      response.message = message;
      return this;
    }

    /**
     * Sets the error code.
     *
     * @param code the error code to set
     * @return this builder instance
     */
    public Builder code(String code) {
      response.code = code;
      return this;
    }

    /**
     * Sets the field errors.
     *
     * @param fieldErrors the list of field errors to set
     * @return this builder instance
     */
    public Builder fieldErrors(List<FieldError> fieldErrors) {
      response.fieldErrors = fieldErrors;
      return this;
    }

    /**
     * Builds and returns the ApiErrorResponse instance.
     *
     * @return the constructed ApiErrorResponse
     */
    public ApiErrorResponse build() {
      return response;
    }
  }
}
