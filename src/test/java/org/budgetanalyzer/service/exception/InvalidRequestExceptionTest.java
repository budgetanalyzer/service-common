package org.budgetanalyzer.service.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParseException;

/** Unit tests for {@link InvalidRequestException}. */
@DisplayName("InvalidRequestException Tests")
class InvalidRequestExceptionTest {

  @Test
  @DisplayName("Should create exception with message only")
  void shouldCreateExceptionWithMessageOnly() {
    var message = "Invalid request format";

    var exception = new InvalidRequestException(message);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should create exception with message and cause")
  void shouldCreateExceptionWithMessageAndCause() {
    var message = "Failed to parse request body";
    var cause = new JsonParseException(null, "Unexpected token");

    var exception = new InvalidRequestException(message, cause);

    assertEquals(message, exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  @Test
  @DisplayName("Should extend ServiceException")
  void shouldExtendServiceException() {
    var exception = new InvalidRequestException("Invalid request");

    assertTrue(exception instanceof ServiceException);
  }

  @Test
  @DisplayName("Should be instance of RuntimeException")
  void shouldBeRuntimeException() {
    var exception = new InvalidRequestException("Invalid request");

    assertTrue(exception instanceof RuntimeException);
  }

  @Test
  @DisplayName("Should handle null message")
  void shouldHandleNullMessage() {
    var exception = new InvalidRequestException(null);

    assertNull(exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should handle null cause")
  void shouldHandleNullCause() {
    var message = "Invalid request";

    var exception = new InvalidRequestException(message, null);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should wrap parsing errors")
  void shouldWrapParsingErrors() {
    var parsingError = new IllegalArgumentException("Invalid date format");
    var exception = new InvalidRequestException("Cannot parse date parameter", parsingError);

    assertEquals("Cannot parse date parameter", exception.getMessage());
    assertSame(parsingError, exception.getCause());
  }

  @Test
  @DisplayName("Should handle missing required parameter scenario")
  void shouldHandleMissingRequiredParameter() {
    var message = "Missing required parameter: userId";

    var exception = new InvalidRequestException(message);

    assertEquals(message, exception.getMessage());
    assertTrue(exception.getMessage().contains("userId"));
  }

  @Test
  @DisplayName("Should handle malformed JSON scenario")
  void shouldHandleMalformedJsonScenario() {
    var message = "Malformed JSON in request body";

    var exception = new InvalidRequestException(message);

    assertEquals(message, exception.getMessage());
  }
}
