package org.budgetanalyzer.service.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a field-level validation error in an API request.
 *
 * <p>This class is used within {@link ApiErrorResponse} when the error type is {@link
 * ApiErrorType#VALIDATION_ERROR} or {@link ApiErrorType#APPLICATION_ERROR} (for batch validation).
 * It provides detailed information about which field failed validation, what value was rejected,
 * and why.
 *
 * <p>Example usage:
 *
 * <pre>
 * // Simple field error
 * FieldError error = FieldError.of(
 *     "email",
 *     "must be a valid email address",
 *     "invalid@email"
 * );
 *
 * // Indexed field error (for batch operations)
 * FieldError batchError = FieldError.of(
 *     44,
 *     "amount",
 *     "must not be null",
 *     null
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
 *     },
 *     {
 *       "index": 44,
 *       "field": "amount",
 *       "message": "must not be null"
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
      description =
          "Zero-based index of the item in a batch/list that caused the error. "
              + "Null for single-item validation errors.",
      example = "44")
  private Integer index;

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

  @Schema(description = "Value that caused the error", example = "invalid@email")
  private Object rejectedValue;

  private FieldError() {}

  /**
   * Private constructor for creating field error instances without index.
   *
   * @param field the field name that failed validation
   * @param message the validation error message
   * @param rejectedValue the value that was rejected
   */
  private FieldError(String field, String message, Object rejectedValue) {
    this.index = null;
    this.field = field;
    this.message = message;
    this.rejectedValue = rejectedValue;
  }

  /**
   * Private constructor for creating indexed field error instances.
   *
   * @param index the zero-based index of the item in the batch
   * @param field the field name that failed validation
   * @param message the validation error message
   * @param rejectedValue the value that was rejected
   */
  private FieldError(Integer index, String field, String message, Object rejectedValue) {
    this.index = index;
    this.field = field;
    this.message = message;
    this.rejectedValue = rejectedValue;
  }

  /**
   * Gets the zero-based index of the item in a batch that caused the error.
   *
   * @return the index, or null for single-item validation errors
   */
  public Integer getIndex() {
    return index;
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
   * Static factory method for creating a FieldError instance without index.
   *
   * @param field the field name that failed validation
   * @param message the validation error message
   * @param rejectedValue the value that was rejected
   * @return a new FieldError instance
   */
  public static FieldError of(String field, String message, Object rejectedValue) {
    return new FieldError(field, message, rejectedValue);
  }

  /**
   * Static factory method for creating an indexed FieldError instance.
   *
   * <p>Use this for batch validation errors where each error is associated with a specific item in
   * a list.
   *
   * @param index the zero-based index of the item in the batch
   * @param field the field name that failed validation
   * @param message the validation error message
   * @param rejectedValue the value that was rejected (may be null)
   * @return a new FieldError instance with index
   */
  public static FieldError of(int index, String field, String message, Object rejectedValue) {
    return new FieldError(index, field, message, rejectedValue);
  }
}
