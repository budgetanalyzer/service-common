package com.bleurubin.service.exception;

/**
 * Exception indicating that a requested resource was not found.
 *
 * <p>This exception represents HTTP 404 Not Found errors and should be thrown when a requested
 * entity or resource does not exist in the system. For example, when querying for a transaction by
 * ID that doesn't exist in the database.
 *
 * <p>Example usage:
 *
 * <pre>
 * var transaction = repository.findById(id)
 *     .orElseThrow(() -&gt; new ResourceNotFoundException("Transaction not found with id: " + id));
 * </pre>
 */
public class ResourceNotFoundException extends ServiceException {

  /**
   * Constructs a new resource not found exception with the specified detail message.
   *
   * @param message the detail message indicating which resource was not found
   */
  public ResourceNotFoundException(String message) {
    super(message);
  }
}
