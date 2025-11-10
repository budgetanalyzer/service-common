package org.budgetanalyzer.service.exception;

/**
 * Base exception for all service-related errors.
 *
 * <p>This exception represents internal server errors (HTTP 500) and is the parent class for all
 * custom exceptions in the service layer. Extend this class to create specific exception types for
 * different error scenarios.
 */
public class ServiceException extends RuntimeException {

  /**
   * Constructs a new service exception with the specified detail message.
   *
   * @param message the detail message explaining the error
   */
  public ServiceException(String message) {
    super(message);
  }

  /**
   * Constructs a new service exception with the specified detail message and cause.
   *
   * @param message the detail message explaining the error
   * @param cause the cause of this exception (which is saved for later retrieval by the {@link
   *     #getCause()} method)
   */
  public ServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
