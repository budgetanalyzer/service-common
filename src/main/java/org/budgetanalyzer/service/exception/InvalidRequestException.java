package org.budgetanalyzer.service.exception;

/**
 * Exception indicating that the request is malformed or invalid.
 *
 * <p>This exception represents HTTP 400 Bad Request errors and should be thrown when the request
 * syntax is incorrect, required parameters are missing, or the request structure is invalid. Use
 * this exception for structural issues with the request itself.
 *
 * <p>For business logic validation errors (e.g., negative amounts, invalid dates), use {@link
 * BusinessException} instead.
 */
public class InvalidRequestException extends ServiceException {

  /**
   * Constructs a new invalid request exception with the specified detail message.
   *
   * @param message the detail message explaining what is wrong with the request
   */
  public InvalidRequestException(String message) {
    super(message);
  }

  /**
   * Constructs a new invalid request exception with the specified detail message and cause.
   *
   * @param message the detail message explaining what is wrong with the request
   * @param cause the underlying cause of this exception (e.g., parsing error)
   */
  public InvalidRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
