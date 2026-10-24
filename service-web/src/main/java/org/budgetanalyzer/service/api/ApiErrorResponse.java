package org.budgetanalyzer.service.api;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * <p>JSON output always uses this property order. Null optional fields ({@code code}, {@code
 * fieldErrors}) are omitted regardless of the application mapper's inclusion settings; empty lists
 * and strings are kept.
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
 *       "message": "Amount must be positive",
 *       "rejectedValue": "-100"
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>Use the strict builder entry point to construct instances:
 *
 * <pre>
 * ApiErrorResponse response = ApiErrorResponse.builder(
 *         ApiErrorType.APPLICATION_ERROR,
 *         "CSV format not supported")
 *     .code("CSV_PARSING_ERROR")
 *     .build();
 * </pre>
 *
 * @see ApiErrorType
 * @see FieldError
 * @see org.budgetanalyzer.service.servlet.api.ServletApiExceptionHandler
 */
@Schema(description = "Standard API error response format")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "message", "code", "fieldErrors"})
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
   * Creates a new builder for constructing ApiErrorResponse instances with required fields.
   *
   * @param type the required error type
   * @param message the required error message
   * @return a new builder instance initialized with required fields
   */
  public static Builder builder(ApiErrorType type, String message) {
    return new Builder()
        .type(Objects.requireNonNull(type, "type must not be null"))
        .message(Objects.requireNonNull(message, "message must not be null"));
  }

  /** Builder class for constructing ApiErrorResponse instances with a fluent API. */
  public static class Builder {
    private final ApiErrorResponse response = new ApiErrorResponse();

    private Builder() {}

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
      Objects.requireNonNull(response.type, "type must not be null");
      Objects.requireNonNull(response.message, "message must not be null");
      return response;
    }
  }
}
