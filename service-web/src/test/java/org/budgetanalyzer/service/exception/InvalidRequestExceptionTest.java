package org.budgetanalyzer.service.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.core.exc.StreamReadException;

/** Unit tests for {@link InvalidRequestException}. */
@DisplayName("InvalidRequestException Tests")
class InvalidRequestExceptionTest {

  @Test
  @DisplayName("Should create exception with cause")
  void shouldCreateExceptionWithCause() {
    var message = "Failed to parse request body";
    var cause = new StreamReadException(null, "Unexpected token");

    var exception = new InvalidRequestException(message, cause);

    assertThat(exception.getCause()).isSameAs(cause);
  }

  @Test
  @DisplayName("Should extend ServiceException")
  void shouldExtendServiceException() {
    var exception = new InvalidRequestException("Invalid request");

    assertThat(exception).isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("Should be instance of RuntimeException")
  void shouldBeRuntimeException() {
    var exception = new InvalidRequestException("Invalid request");

    assertThat(exception).isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("Should handle null cause")
  void shouldHandleNullCause() {
    var message = "Invalid request";

    var exception = new InvalidRequestException(message, null);

    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should wrap parsing errors")
  void shouldWrapParsingErrors() {
    var parsingError = new IllegalArgumentException("Invalid date format");
    var exception = new InvalidRequestException("Cannot parse date parameter", parsingError);

    assertThat(exception.getCause()).isSameAs(parsingError);
  }
}
