package com.bleurubin.service.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

  private CorrelationIdFilter filter;

  @BeforeEach
  void setUp() {
    filter = new CorrelationIdFilter();
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
    filter.doFilterInternal(request, response, filterChain);

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
    filter.doFilterInternal(request, response, filterChain);

    // Assert
    verify(response).setHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldStoreCorrelationIdInMDC() throws Exception {
    // Arrange
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

    // Act
    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          // Verify MDC is set during filter chain execution
          String mdcValue = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
          assertNotNull(mdcValue);
          assertTrue(mdcValue.startsWith("req_"));
        });

    // Assert - MDC should be cleared after filter execution
    assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
  }

  @Test
  void shouldClearMDCAfterFilterExecution() throws Exception {
    // Arrange
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert
    assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
  }

  @Test
  void shouldClearMDCEvenWhenExceptionIsThrown() throws Exception {
    // Arrange
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);
    doThrow(new RuntimeException("Simulated error")).when(filterChain).doFilter(request, response);

    // Act & Assert
    assertThrows(
        RuntimeException.class, () -> filter.doFilterInternal(request, response, filterChain));

    // MDC should still be cleared
    assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
  }

  @Test
  void shouldGenerateUniqueCorrelationIds() throws Exception {
    // Arrange
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

    // Act - Call filter multiple times
    var correlationIds = new String[10];
    for (int i = 0; i < 10; i++) {
      final int index = i;
      filter.doFilterInternal(
          request,
          response,
          (req, res) -> {
            correlationIds[index] = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
          });
    }

    // Assert - All should be unique
    assertEquals(10, java.util.Arrays.stream(correlationIds).distinct().count());
  }

  @Test
  void shouldHandleEmptyCorrelationIdHeader() throws Exception {
    // Arrange
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("  ");

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert - Should generate new ID when header is empty/whitespace
    verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), startsWith("req_"));
  }

  @Test
  void shouldGenerateCorrelationIdWithCorrectFormat() throws Exception {
    // Arrange
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

    // Act
    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);

          // Assert format: req_<16 hex chars>
          assertNotNull(correlationId);
          assertTrue(correlationId.startsWith("req_"));
          assertEquals(20, correlationId.length()); // "req_" (4) + 16 hex chars
          assertTrue(
              correlationId.substring(4).matches("[0-9a-f]{16}"),
              "Correlation ID should contain 16 hexadecimal characters");
        });
  }
}
