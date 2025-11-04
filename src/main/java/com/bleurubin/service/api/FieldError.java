package com.bleurubin.service.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a field-level validation error in an API request.
 *
 * <p>This class is used within {@link ApiErrorResponse} when the error type is {@link
 * ApiErrorType#VALIDATION_ERROR}. It provides detailed information about which field failed
 * validation, what value was rejected, and why.
 *
 * <p>Example usage:
 *
 * <pre>
 * FieldError error = FieldError.of(
 *     "email",
 *     "must be a valid email address",
 *     "invalid@email"
 * );
 * </pre>
 *
 * <p>In JSON responses, field errors appear in the {@code fieldErrors} array:
 *
 * <pre>
 * {
 *   "type": "VALIDATION_ERROR",
 *   "message": "One or more fields have validation errors",
 *   "fieldErrors": [
 *     {
 *       "field": "email",
 *       "message": "must be a valid email address",
 *       "rejectedValue": "invalid@email"
 *     }
 *   ]
 * }
 * </pre>
 *
 * @see ApiErrorResponse
 * @see ApiErrorType#VALIDATION_ERROR
 */
@Schema(description = "Field-level validation error details")
public class FieldError {

  @Schema(
      description = "Field that triggered the error",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example = "email")
  private String field;

  @Schema(
      description = "Error message",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example = "must be a valid email address")
  private String message;

  @Schema(
      description = "Value that caused the error",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example = "invalid@email")
  private Object rejectedValue;

  private FieldError() {}

  /**
   * Private constructor for creating field error instances.
   *
   * @param field the field name that failed validation
   * @param message the validation error message
   * @param rejectedValue the value that was rejected
   */
  private FieldError(String field, String message, Object rejectedValue) {
    this.field = field;
    this.message = message;
    this.rejectedValue = rejectedValue;
  }

  /**
   * Gets the field name that failed validation.
   *
   * @return the field name
   */
  public String getField() {
    return field;
  }

  /**
   * Gets the validation error message.
   *
   * @return the error message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Gets the value that was rejected during validation.
   *
   * @return the rejected value
   */
  public Object getRejectedValue() {
    return rejectedValue;
  }

  /**
   * Static factory method for creating a FieldError instance.
   *
   * @param field the field name that failed validation
   * @param message the validation error message
   * @param rejectedValue the value that was rejected
   * @return a new FieldError instance
   */
  public static FieldError of(String field, String message, Object rejectedValue) {
    return new FieldError(field, message, rejectedValue);
  }
}
