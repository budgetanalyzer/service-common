package org.budgetanalyzer.service.exception;

import java.util.Collections;
import java.util.List;

import org.budgetanalyzer.service.api.FieldError;

/**
 * Exception indicating a business rule violation.
 *
 * <p>This exception represents HTTP 422 Unprocessable Entity errors and should be thrown when the
 * request is well-formed but cannot be processed due to business logic constraints. Unlike {@link
 * InvalidRequestException}, this indicates the request structure is valid, but the operation
 * violates domain-specific business rules.
 *
 * <p>Examples include:
 *
 * <ul>
 *   <li>Attempting to create a transaction with a negative amount
 *   <li>Exceeding a budget limit
 *   <li>Duplicate transaction detection
 *   <li>Invalid state transitions
 *   <li>Batch validation failures (with field-level errors)
 * </ul>
 *
 * <p>Each business exception includes a machine-readable error code that can be used by clients for
 * localization or specific error handling. Optionally, it may include field-level errors for batch
 * validation scenarios.
 */
public class BusinessException extends ServiceException {

  /** Machine-readable error code for this business rule violation. */
  private final String code;

  /** Optional field-level errors for batch validation failures. */
  private final List<FieldError> fieldErrors;

  /**
   * Constructs a new business exception with the specified message and error code.
   *
   * @param message the human-readable detail message explaining the business rule violation
   * @param code the machine-readable error code (e.g., "NEGATIVE_AMOUNT", "BUDGET_EXCEEDED")
   */
  public BusinessException(String message, String code) {
    super(message);
    this.code = code;
    this.fieldErrors = Collections.emptyList();
  }

  /**
   * Constructs a new business exception with the specified message, error code, and cause.
   *
   * @param message the human-readable detail message explaining the business rule violation
   * @param code the machine-readable error code (e.g., "NEGATIVE_AMOUNT", "BUDGET_EXCEEDED")
   * @param cause the underlying cause of this exception
   */
  public BusinessException(String message, String code, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.fieldErrors = Collections.emptyList();
  }

  /**
   * Constructs a new business exception with field-level validation errors.
   *
   * <p>Use this constructor for batch validation failures where each error is associated with a
   * specific field and optionally an index in the batch.
   *
   * @param message the human-readable detail message
   * @param code the machine-readable error code
   * @param fieldErrors the list of field-level validation errors
   */
  public BusinessException(String message, String code, List<FieldError> fieldErrors) {
    super(message);
    this.code = code;
    this.fieldErrors = fieldErrors != null ? List.copyOf(fieldErrors) : Collections.emptyList();
  }

  /**
   * Gets the machine-readable error code for this business exception.
   *
   * @return the error code
   */
  public String getCode() {
    return code;
  }

  /**
   * Gets the field-level validation errors, if any.
   *
   * @return an unmodifiable list of field errors, empty if none
   */
  public List<FieldError> getFieldErrors() {
    return fieldErrors;
  }

  /**
   * Checks if this exception has field-level errors.
   *
   * @return true if there are field errors, false otherwise
   */
  public boolean hasFieldErrors() {
    return !fieldErrors.isEmpty();
  }
}
