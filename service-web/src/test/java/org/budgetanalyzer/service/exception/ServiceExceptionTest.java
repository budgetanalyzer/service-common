package org.budgetanalyzer.service.exception;

import static org.assertj.core.api.Assertions.assertThat;

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

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should create exception with message and cause")
  void shouldCreateExceptionWithMessageAndCause() {
    var message = "Service error occurred";
    var cause = new IOException("Database connection failed");

    var exception = new ServiceException(message, cause);

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getCause()).isSameAs(cause);
  }

  @Test
  @DisplayName("Should handle null message")
  void shouldHandleNullMessage() {
    var exception = new ServiceException(null);

    assertThat(exception.getMessage()).isNull();
    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should handle null message with cause")
  void shouldHandleNullMessageWithCause() {
    var cause = new IOException("Network error");

    var exception = new ServiceException(null, cause);

    assertThat(exception.getMessage()).isNull();
    assertThat(exception.getCause()).isSameAs(cause);
  }

  @Test
  @DisplayName("Should handle null cause")
  void shouldHandleNullCause() {
    var message = "Service error occurred";

    var exception = new ServiceException(message, null);

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should be instance of RuntimeException")
  void shouldBeRuntimeException() {
    var exception = new ServiceException("Error");

    assertThat(exception).isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("Should preserve stack trace")
  void shouldPreserveStackTrace() {
    var exception = new ServiceException("Error");

    assertThat(exception.getStackTrace()).isNotNull();
    assertThat(exception.getStackTrace().length > 0).isTrue();
  }

  @Test
  @DisplayName("Should preserve nested cause chain")
  void shouldPreserveNestedCauseChain() {
    var rootCause = new IllegalArgumentException("Invalid argument");
    var intermediateCause = new IOException("IO error", rootCause);
    var exception = new ServiceException("Service error", intermediateCause);

    assertThat(exception.getMessage()).isEqualTo("Service error");
    assertThat(exception.getCause()).isSameAs(intermediateCause);
    assertThat(exception.getCause().getCause()).isSameAs(rootCause);
  }

  @Test
  @DisplayName("Should handle empty message")
  void shouldHandleEmptyMessage() {
    var exception = new ServiceException("");

    assertThat(exception.getMessage()).isEqualTo("");
  }

  @Test
  @DisplayName("Should handle multi-line message")
  void shouldHandleMultiLineMessage() {
    var message = "Line 1\nLine 2\nLine 3";

    var exception = new ServiceException(message);

    assertThat(exception.getMessage()).isEqualTo(message);
  }
}
