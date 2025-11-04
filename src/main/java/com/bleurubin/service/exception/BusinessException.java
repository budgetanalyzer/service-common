package com.bleurubin.service.exception;

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
 * </ul>
 *
 * <p>Each business exception includes a machine-readable error code that can be used by clients for
 * localization or specific error handling.
 */
public class BusinessException extends ServiceException {

  /** Machine-readable error code for this business rule violation. */
  private final String code;

  /**
   * Constructs a new business exception with the specified message and error code.
   *
   * @param message the human-readable detail message explaining the business rule violation
   * @param code the machine-readable error code (e.g., "NEGATIVE_AMOUNT", "BUDGET_EXCEEDED")
   */
  public BusinessException(String message, String code) {
    super(message);
    this.code = code;
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
  }

  /**
   * Gets the machine-readable error code for this business exception.
   *
   * @return the error code
   */
  public String getCode() {
    return code;
  }
}
