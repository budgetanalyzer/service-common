package com.bleurubin.service.exception;

/**
 * Exception indicating that a required service is temporarily unavailable.
 *
 * <p>This exception represents HTTP 503 Service Unavailable errors and should be thrown when a
 * downstream service, external API, or database is temporarily unreachable or not responding. This
 * signals to callers that the request may succeed if retried later.
 *
 * <p>Examples include:
 *
 * <ul>
 *   <li>Database connection failures
 *   <li>External API timeouts or connection errors
 *   <li>Circuit breaker open states
 *   <li>Downstream microservice unavailability
 * </ul>
 */
public class ServiceUnavailableException extends ServiceException {

  /**
   * Constructs a new service unavailable exception with the specified detail message.
   *
   * @param message the detail message indicating which service is unavailable
   */
  public ServiceUnavailableException(String message) {
    super(message);
  }

  /**
   * Constructs a new service unavailable exception with the specified detail message and cause.
   *
   * @param message the detail message indicating which service is unavailable
   * @param cause the underlying cause (e.g., connection timeout, network error)
   */
  public ServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
