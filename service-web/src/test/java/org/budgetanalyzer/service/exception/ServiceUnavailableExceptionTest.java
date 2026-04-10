package org.budgetanalyzer.service.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ServiceUnavailableException}. */
@DisplayName("ServiceUnavailableException Tests")
class ServiceUnavailableExceptionTest {

  @Test
  @DisplayName("Should create exception with message only")
  void shouldCreateExceptionWithMessageOnly() {
    var message = "Database connection unavailable";

    var exception = new ServiceUnavailableException(message);

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should create exception with message and cause")
  void shouldCreateExceptionWithMessageAndCause() {
    var message = "External API unavailable";
    var cause = new ConnectException("Connection refused");

    var exception = new ServiceUnavailableException(message, cause);

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getCause()).isSameAs(cause);
  }

  @Test
  @DisplayName("Should extend ServiceException")
  void shouldExtendServiceException() {
    var exception = new ServiceUnavailableException("Service unavailable");

    assertThat(exception).isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("Should be instance of RuntimeException")
  void shouldBeRuntimeException() {
    var exception = new ServiceUnavailableException("Service unavailable");

    assertThat(exception).isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("Should handle null message")
  void shouldHandleNullMessage() {
    var exception = new ServiceUnavailableException(null);

    assertThat(exception.getMessage()).isNull();
    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should handle null cause")
  void shouldHandleNullCause() {
    var message = "Service unavailable";

    var exception = new ServiceUnavailableException(message, null);

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should wrap database connection failures")
  void shouldWrapDatabaseConnectionFailures() {
    var sqlException = new RuntimeException("Database connection timeout");
    var exception = new ServiceUnavailableException("Cannot connect to database", sqlException);

    assertThat(exception.getMessage()).isEqualTo("Cannot connect to database");
    assertThat(exception.getCause()).isSameAs(sqlException);
  }

  @Test
  @DisplayName("Should wrap external API timeouts")
  void shouldWrapExternalApiTimeouts() {
    var timeoutException = new TimeoutException("Request timeout after 30s");
    var exception = new ServiceUnavailableException("Currency API timeout", timeoutException);

    assertThat(exception.getMessage()).isEqualTo("Currency API timeout");
    assertThat(exception.getCause()).isSameAs(timeoutException);
  }

  @Test
  @DisplayName("Should wrap network connection errors")
  void shouldWrapNetworkConnectionErrors() {
    var networkException = new IOException("Network unreachable");
    var exception =
        new ServiceUnavailableException("Failed to reach payment gateway", networkException);

    assertThat(exception.getMessage()).isEqualTo("Failed to reach payment gateway");
    assertThat(exception.getCause()).isSameAs(networkException);
  }

  @Test
  @DisplayName("Should indicate downstream service failure")
  void shouldIndicateDownstreamServiceFailure() {
    var message = "Authentication service is down";

    var exception = new ServiceUnavailableException(message);

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getMessage().contains("down")).isTrue();
  }

  @Test
  @DisplayName("Should indicate circuit breaker scenario")
  void shouldIndicateCircuitBreakerScenario() {
    var message = "Circuit breaker open for payment service";

    var exception = new ServiceUnavailableException(message);

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getMessage().contains("Circuit breaker")).isTrue();
  }
}
