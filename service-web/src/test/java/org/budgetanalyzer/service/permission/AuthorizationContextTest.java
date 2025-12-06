package org.budgetanalyzer.service.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for AuthorizationContext. */
class AuthorizationContextTest {

  private static final String USER_ID = "auth0|123";

  @Nested
  @DisplayName("of() factory method")
  class OfTests {

    @Test
    @DisplayName("should create context with only userId")
    void shouldCreateContextWithOnlyUserId() {
      // When
      var ctx = AuthorizationContext.of(USER_ID);

      // Then
      assertThat(ctx.userId()).isEqualTo(USER_ID);
      assertThat(ctx.clientIp()).isNull();
      assertThat(ctx.correlationId()).isNull();
      assertThat(ctx.sourceService()).isNull();
    }
  }

  @Nested
  @DisplayName("fromRequest() factory method")
  class FromRequestTests {

    @Test
    @DisplayName("should extract all fields from request")
    void shouldExtractAllFieldsFromRequest() {
      // Given
      var request = mock(HttpServletRequest.class);
      when(request.getRemoteAddr()).thenReturn("10.0.0.1");
      when(request.getHeader("X-Forwarded-For")).thenReturn(null);
      when(request.getHeader("X-Correlation-ID")).thenReturn("corr-123");
      when(request.getHeader("X-Source-Service")).thenReturn("api-gateway");

      // When
      var ctx = AuthorizationContext.fromRequest(USER_ID, request);

      // Then
      assertThat(ctx.userId()).isEqualTo(USER_ID);
      assertThat(ctx.clientIp()).isEqualTo("10.0.0.1");
      assertThat(ctx.correlationId()).isEqualTo("corr-123");
      assertThat(ctx.sourceService()).isEqualTo("api-gateway");
    }

    @Test
    @DisplayName("should use X-Forwarded-For when present")
    void shouldUseForwardedForHeaderWhenPresent() {
      // Given
      var request = mock(HttpServletRequest.class);
      when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195");
      when(request.getRemoteAddr()).thenReturn("10.0.0.1");
      when(request.getHeader("X-Correlation-ID")).thenReturn(null);
      when(request.getHeader("X-Source-Service")).thenReturn(null);

      // When
      var ctx = AuthorizationContext.fromRequest(USER_ID, request);

      // Then
      assertThat(ctx.clientIp()).isEqualTo("203.0.113.195");
    }

    @Test
    @DisplayName("should use first IP from X-Forwarded-For chain")
    void shouldUseFirstIpFromForwardedForChain() {
      // Given
      var request = mock(HttpServletRequest.class);
      when(request.getHeader("X-Forwarded-For"))
          .thenReturn("203.0.113.195, 70.41.3.18, 150.172.238.178");
      when(request.getRemoteAddr()).thenReturn("10.0.0.1");
      when(request.getHeader("X-Correlation-ID")).thenReturn(null);
      when(request.getHeader("X-Source-Service")).thenReturn(null);

      // When
      var ctx = AuthorizationContext.fromRequest(USER_ID, request);

      // Then
      assertThat(ctx.clientIp()).isEqualTo("203.0.113.195");
    }

    @Test
    @DisplayName("should fallback to remoteAddr when X-Forwarded-For is empty")
    void shouldFallbackToRemoteAddrWhenForwardedForIsEmpty() {
      // Given
      var request = mock(HttpServletRequest.class);
      when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
      when(request.getRemoteAddr()).thenReturn("10.0.0.1");
      when(request.getHeader("X-Correlation-ID")).thenReturn(null);
      when(request.getHeader("X-Source-Service")).thenReturn(null);

      // When
      var ctx = AuthorizationContext.fromRequest(USER_ID, request);

      // Then
      assertThat(ctx.clientIp()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("should handle missing optional headers")
    void shouldHandleMissingOptionalHeaders() {
      // Given
      var request = mock(HttpServletRequest.class);
      when(request.getRemoteAddr()).thenReturn("10.0.0.1");
      when(request.getHeader("X-Forwarded-For")).thenReturn(null);
      when(request.getHeader("X-Correlation-ID")).thenReturn(null);
      when(request.getHeader("X-Source-Service")).thenReturn(null);

      // When
      var ctx = AuthorizationContext.fromRequest(USER_ID, request);

      // Then
      assertThat(ctx.correlationId()).isNull();
      assertThat(ctx.sourceService()).isNull();
    }
  }
}
