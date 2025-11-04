package com.bleurubin.service.http;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that generates or extracts a correlation ID for each HTTP request.
 *
 * <p>The correlation ID is used for distributed tracing and request tracking across microservices.
 * It is stored in MDC (Mapped Diagnostic Context) so all subsequent log entries for this request
 * will include the correlation ID.
 *
 * <p>Order: -100 (runs early in filter chain, before logging filter)
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class CorrelationIdFilter extends OncePerRequestFilter {

  /** Header name for correlation ID. */
  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

  /** MDC key for correlation ID. */
  public static final String CORRELATION_ID_MDC_KEY = "correlationId";

  /** Prefix for generated correlation IDs. */
  private static final String CORRELATION_ID_PREFIX = "req_";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var correlationId = extractOrGenerateCorrelationId(request);

    // Store in MDC for logging
    MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

    // Add to response headers for client-side tracing
    response.setHeader(CORRELATION_ID_HEADER, correlationId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      // Clean up MDC to prevent memory leaks
      MDC.remove(CORRELATION_ID_MDC_KEY);
    }
  }

  /**
   * Extracts correlation ID from request header, or generates a new one if not present.
   *
   * @param request The HTTP request
   * @return Correlation ID
   */
  private String extractOrGenerateCorrelationId(HttpServletRequest request) {
    var correlationId = request.getHeader(CORRELATION_ID_HEADER);

    if (correlationId == null || correlationId.trim().isEmpty()) {
      correlationId = generateCorrelationId();
    }

    return correlationId;
  }

  /**
   * Generates a new correlation ID.
   *
   * <p>Format: req_<16-hex-chars>
   *
   * @return Generated correlation ID
   */
  private String generateCorrelationId() {
    var uuid = UUID.randomUUID().toString().replace("-", "");
    return CORRELATION_ID_PREFIX + uuid.substring(0, 16);
  }
}
