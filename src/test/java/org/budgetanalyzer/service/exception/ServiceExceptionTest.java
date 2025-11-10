package org.budgetanalyzer.service.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ServiceException}. */
@DisplayName("ServiceException Tests")
class ServiceExceptionTest {

  @Test
  @DisplayName("Should create exception with message only")
  void shouldCreateExceptionWithMessageOnly() {
    var message = "Service error occurred";

    var exception = new ServiceException(message);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should create exception with message and cause")
  void shouldCreateExceptionWithMessageAndCause() {
    var message = "Service error occurred";
    var cause = new IOException("Database connection failed");

    var exception = new ServiceException(message, cause);

    assertEquals(message, exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  @Test
  @DisplayName("Should handle null message")
  void shouldHandleNullMessage() {
    var exception = new ServiceException(null);

    assertNull(exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should handle null message with cause")
  void shouldHandleNullMessageWithCause() {
    var cause = new IOException("Network error");

    var exception = new ServiceException(null, cause);

    assertNull(exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  @Test
  @DisplayName("Should handle null cause")
  void shouldHandleNullCause() {
    var message = "Service error occurred";

    var exception = new ServiceException(message, null);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should be instance of RuntimeException")
  void shouldBeRuntimeException() {
    var exception = new ServiceException("Error");

    assertTrue(exception instanceof RuntimeException);
  }

  @Test
  @DisplayName("Should preserve stack trace")
  void shouldPreserveStackTrace() {
    var exception = new ServiceException("Error");

    assertNotNull(exception.getStackTrace());
    assertTrue(exception.getStackTrace().length > 0);
  }

  @Test
  @DisplayName("Should preserve nested cause chain")
  void shouldPreserveNestedCauseChain() {
    var rootCause = new IllegalArgumentException("Invalid argument");
    var intermediateCause = new IOException("IO error", rootCause);
    var exception = new ServiceException("Service error", intermediateCause);

    assertEquals("Service error", exception.getMessage());
    assertSame(intermediateCause, exception.getCause());
    assertSame(rootCause, exception.getCause().getCause());
  }

  @Test
  @DisplayName("Should handle empty message")
  void shouldHandleEmptyMessage() {
    var exception = new ServiceException("");

    assertEquals("", exception.getMessage());
  }

  @Test
  @DisplayName("Should handle multi-line message")
  void shouldHandleMultiLineMessage() {
    var message = "Line 1\nLine 2\nLine 3";

    var exception = new ServiceException(message);

    assertEquals(message, exception.getMessage());
  }
}
