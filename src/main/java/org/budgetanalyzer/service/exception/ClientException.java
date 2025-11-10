package org.budgetanalyzer.service.exception;

/**
 * Exception indicating a client error in the request.
 *
 * <p>This exception represents client errors when making requests to external services or APIs.
 * Typically wraps an exception from an HTTP client library.
 */
public class ClientException extends ServiceException {

  /**
   * Constructs a new client exception with the specified detail message.
   *
   * @param message the detail message explaining what was wrong with the client request
   */
  public ClientException(String message) {
    super(message);
  }

  /**
   * Constructs a new client exception with the specified detail message and cause.
   *
   * @param message the detail message explaining what was wrong with the client request
   * @param cause the underlying cause of this exception
   */
  public ClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
