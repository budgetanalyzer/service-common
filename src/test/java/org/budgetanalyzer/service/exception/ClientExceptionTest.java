package org.budgetanalyzer.service.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ClientException}. */
@DisplayName("ClientException Tests")
class ClientExceptionTest {

  @Test
  @DisplayName("Should create exception with message only")
  void shouldCreateExceptionWithMessageOnly() {
    var message = "Client request failed";

    var exception = new ClientException(message);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should create exception with message and cause")
  void shouldCreateExceptionWithMessageAndCause() {
    var message = "Failed to connect to external API";
    var cause = new IOException("Connection timeout");

    var exception = new ClientException(message, cause);

    assertEquals(message, exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  @Test
  @DisplayName("Should extend ServiceException")
  void shouldExtendServiceException() {
    var exception = new ClientException("Error");

    assertTrue(exception instanceof ServiceException);
  }

  @Test
  @DisplayName("Should be instance of RuntimeException")
  void shouldBeRuntimeException() {
    var exception = new ClientException("Error");

    assertTrue(exception instanceof RuntimeException);
  }

  @Test
  @DisplayName("Should handle null message")
  void shouldHandleNullMessage() {
    var exception = new ClientException(null);

    assertNull(exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should handle null cause")
  void shouldHandleNullCause() {
    var message = "Client error";

    var exception = new ClientException(message, null);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should preserve cause when wrapping HTTP client exceptions")
  void shouldPreserveCauseWhenWrappingHttpExceptions() {
    var httpException = new RuntimeException("HTTP 503 Service Unavailable");
    var exception = new ClientException("External service unavailable", httpException);

    assertEquals("External service unavailable", exception.getMessage());
    assertSame(httpException, exception.getCause());
    assertEquals("HTTP 503 Service Unavailable", exception.getCause().getMessage());
  }
}
