package org.budgetanalyzer.service.exception;

import static org.assertj.core.api.Assertions.assertThat;

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

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should create exception with message and cause")
  void shouldCreateExceptionWithMessageAndCause() {
    var message = "Failed to parse request body";
    var cause = new JsonParseException(null, "Unexpected token");

    var exception = new InvalidRequestException(message, cause);

    assertThat(exception.getMessage()).isEqualTo(message);
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
  @DisplayName("Should handle null message")
  void shouldHandleNullMessage() {
    var exception = new InvalidRequestException(null);

    assertThat(exception.getMessage()).isNull();
    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should handle null cause")
  void shouldHandleNullCause() {
    var message = "Invalid request";

    var exception = new InvalidRequestException(message, null);

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should wrap parsing errors")
  void shouldWrapParsingErrors() {
    var parsingError = new IllegalArgumentException("Invalid date format");
    var exception = new InvalidRequestException("Cannot parse date parameter", parsingError);

    assertThat(exception.getMessage()).isEqualTo("Cannot parse date parameter");
    assertThat(exception.getCause()).isSameAs(parsingError);
  }

  @Test
  @DisplayName("Should handle missing required parameter scenario")
  void shouldHandleMissingRequiredParameter() {
    var message = "Missing required parameter: userId";

    var exception = new InvalidRequestException(message);

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getMessage().contains("userId")).isTrue();
  }

  @Test
  @DisplayName("Should handle malformed JSON scenario")
  void shouldHandleMalformedJsonScenario() {
    var message = "Malformed JSON in request body";

    var exception = new InvalidRequestException(message);

    assertThat(exception.getMessage()).isEqualTo(message);
  }
}
