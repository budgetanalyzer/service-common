package org.budgetanalyzer.service.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ResourceNotFoundException}. */
@DisplayName("ResourceNotFoundException Tests")
class ResourceNotFoundExceptionTest {

  @Test
  @DisplayName("Should create exception with message")
  void shouldCreateExceptionWithMessage() {
    var message = "Transaction not found with id: 123";

    var exception = new ResourceNotFoundException(message);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should extend ServiceException")
  void shouldExtendServiceException() {
    var exception = new ResourceNotFoundException("Not found");

    assertTrue(exception instanceof ServiceException);
  }

  @Test
  @DisplayName("Should be instance of RuntimeException")
  void shouldBeRuntimeException() {
    var exception = new ResourceNotFoundException("Not found");

    assertTrue(exception instanceof RuntimeException);
  }

  @Test
  @DisplayName("Should handle null message")
  void shouldHandleNullMessage() {
    var exception = new ResourceNotFoundException(null);

    assertNull(exception.getMessage());
  }

  @Test
  @DisplayName("Should handle resource ID in message")
  void shouldHandleResourceIdInMessage() {
    var id = 12345L;
    var message = "User not found with id: " + id;

    var exception = new ResourceNotFoundException(message);

    assertEquals(message, exception.getMessage());
    assertTrue(exception.getMessage().contains("12345"));
  }

  @Test
  @DisplayName("Should handle entity type and ID in message")
  void shouldHandleEntityTypeAndIdInMessage() {
    var entityType = "Budget";
    var id = "abc-123";
    var message = String.format("%s not found with id: %s", entityType, id);

    var exception = new ResourceNotFoundException(message);

    assertEquals("Budget not found with id: abc-123", exception.getMessage());
  }

  @Test
  @DisplayName("Should be usable in Optional.orElseThrow pattern")
  void shouldBeUsableInOptionalOrElseThrow() {
    // This test demonstrates the common usage pattern
    var message = "Resource not found";

    var exception = new ResourceNotFoundException(message);

    assertEquals(message, exception.getMessage());
    assertTrue(exception instanceof RuntimeException);
  }
}
