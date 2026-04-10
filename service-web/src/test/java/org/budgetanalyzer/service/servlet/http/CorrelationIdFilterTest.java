package org.budgetanalyzer.service.servlet.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

  @Mock private HttpServletRequest request;

  @Mock private HttpServletResponse response;

  @Mock private FilterChain filterChain;

  private CorrelationIdFilter correlationIdFilter;

  @BeforeEach
  void setUp() {
    correlationIdFilter = new CorrelationIdFilter();
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void shouldGenerateCorrelationIdWhenNotProvidedInRequest() throws Exception {
    // Arrange
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

    // Act
    correlationIdFilter.doFilterInternal(request, response, filterChain);

    // Assert
    verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), startsWith("req_"));
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldUseExistingCorrelationIdFromRequest() throws Exception {
    // Arrange
    var existingCorrelationId = "req_abc123def456";
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
        .thenReturn(existingCorrelationId);

    // Act
    correlationIdFilter.doFilterInternal(request, response, filterChain);

    // Assert
    verify(response).setHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldTrimExistingCorrelationIdFromRequest() throws Exception {
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
        .thenReturn("  req_trimmed-123  ");

    correlationIdFilter.doFilterInternal(request, response, filterChain);

    verify(response).setHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "req_trimmed-123");
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldStoreCorrelationIdInMdc() throws Exception {
    // Arrange
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

    // Act
    correlationIdFilter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          // Verify MDC is set during filter chain execution
          String mdcValue = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
          assertThat(mdcValue).isNotNull();
          assertThat(mdcValue.startsWith("req_")).isTrue();
        });

    // Assert - MDC should be cleared after filter execution
    assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
  }

  @Test
  void shouldClearMdcAfterFilterExecution() throws Exception {
    // Arrange
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

    // Act
    correlationIdFilter.doFilterInternal(request, response, filterChain);

    // Assert
    assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
  }

  @Test
  void shouldClearMdcEvenWhenExceptionIsThrown() throws Exception {
    // Arrange
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);
    doThrow(new RuntimeException("Simulated error")).when(filterChain).doFilter(request, response);

    // Act & Assert
    assertThatThrownBy(() -> correlationIdFilter.doFilterInternal(request, response, filterChain))
        .isInstanceOf(RuntimeException.class);

    // MDC should still be cleared
    assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
  }

  @Test
  void shouldGenerateUniqueCorrelationIds() throws Exception {
    // Arrange
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

    // Act - Call filter multiple times
    var correlationIds = new String[10];
    for (int i = 0; i < 10; i++) {
      final int index = i;
      correlationIdFilter.doFilterInternal(
          request,
          response,
          (req, res) -> {
            correlationIds[index] = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
          });
    }

    // Assert - All should be unique
    assertThat(Arrays.stream(correlationIds).distinct().count()).isEqualTo(10);
  }

  @Test
  void shouldHandleEmptyCorrelationIdHeader() throws Exception {
    // Arrange
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("  ");

    // Act
    correlationIdFilter.doFilterInternal(request, response, filterChain);

    // Assert - Should generate new ID when header is empty/whitespace
    verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), startsWith("req_"));
  }

  @Test
  void shouldGenerateCorrelationIdWhenHeaderContainsUnsafeCharacters() throws Exception {
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("bad value");

    correlationIdFilter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          var correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
          assertThat(correlationId).isNotNull();
          assertThat(correlationId.startsWith("req_")).isTrue();
          assertThat(correlationId.length()).isEqualTo(36);
        });

    verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), startsWith("req_"));
  }

  @Test
  void shouldGenerateCorrelationIdWhenHeaderExceedsMaxLength() throws Exception {
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("a".repeat(129));

    correlationIdFilter.doFilterInternal(request, response, filterChain);

    verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), startsWith("req_"));
  }

  @Test
  void shouldGenerateCorrelationIdWithCorrectFormat() throws Exception {
    // Arrange
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

    // Act
    correlationIdFilter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);

          // Assert format: req_<32 hex chars>
          assertThat(correlationId).isNotNull();
          assertThat(correlationId.startsWith("req_")).isTrue();
          assertThat(correlationId.length()).isEqualTo(36); // "req_" (4) + 32 hex chars
          assertThat(correlationId.substring(4).matches("[0-9a-f]{32}"))
              .as("Correlation ID should contain 32 hexadecimal characters")
              .isTrue();
        });
  }
}
