package com.bleurubin.service.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should create exception with message and cause")
  void shouldCreateExceptionWithMessageAndCause() {
    var message = "External API unavailable";
    var cause = new ConnectException("Connection refused");

    var exception = new ServiceUnavailableException(message, cause);

    assertEquals(message, exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  @Test
  @DisplayName("Should extend ServiceException")
  void shouldExtendServiceException() {
    var exception = new ServiceUnavailableException("Service unavailable");

    assertTrue(exception instanceof ServiceException);
  }

  @Test
  @DisplayName("Should be instance of RuntimeException")
  void shouldBeRuntimeException() {
    var exception = new ServiceUnavailableException("Service unavailable");

    assertTrue(exception instanceof RuntimeException);
  }

  @Test
  @DisplayName("Should handle null message")
  void shouldHandleNullMessage() {
    var exception = new ServiceUnavailableException(null);

    assertNull(exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should handle null cause")
  void shouldHandleNullCause() {
    var message = "Service unavailable";

    var exception = new ServiceUnavailableException(message, null);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should wrap database connection failures")
  void shouldWrapDatabaseConnectionFailures() {
    var sqlException = new RuntimeException("Database connection timeout");
    var exception = new ServiceUnavailableException("Cannot connect to database", sqlException);

    assertEquals("Cannot connect to database", exception.getMessage());
    assertSame(sqlException, exception.getCause());
  }

  @Test
  @DisplayName("Should wrap external API timeouts")
  void shouldWrapExternalApiTimeouts() {
    var timeoutException = new TimeoutException("Request timeout after 30s");
    var exception = new ServiceUnavailableException("Currency API timeout", timeoutException);

    assertEquals("Currency API timeout", exception.getMessage());
    assertSame(timeoutException, exception.getCause());
  }

  @Test
  @DisplayName("Should wrap network connection errors")
  void shouldWrapNetworkConnectionErrors() {
    var networkException = new IOException("Network unreachable");
    var exception =
        new ServiceUnavailableException("Failed to reach payment gateway", networkException);

    assertEquals("Failed to reach payment gateway", exception.getMessage());
    assertSame(networkException, exception.getCause());
  }

  @Test
  @DisplayName("Should indicate downstream service failure")
  void shouldIndicateDownstreamServiceFailure() {
    var message = "Authentication service is down";

    var exception = new ServiceUnavailableException(message);

    assertEquals(message, exception.getMessage());
    assertTrue(exception.getMessage().contains("down"));
  }

  @Test
  @DisplayName("Should indicate circuit breaker scenario")
  void shouldIndicateCircuitBreakerScenario() {
    var message = "Circuit breaker open for payment service";

    var exception = new ServiceUnavailableException(message);

    assertEquals(message, exception.getMessage());
    assertTrue(exception.getMessage().contains("Circuit breaker"));
  }
}
