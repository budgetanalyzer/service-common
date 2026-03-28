package org.budgetanalyzer.service.servlet.http;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.budgetanalyzer.core.logging.CorrelationIdResolver;

/**
 * Filter that generates or extracts a safe correlation ID for each HTTP request.
 *
 * <p>The correlation ID is used for distributed tracing and request tracking across microservices.
 * It is stored in MDC (Mapped Diagnostic Context) so all subsequent log entries for this request
 * will include the correlation ID. Malformed inbound values are discarded and replaced with a new
 * generated ID.
 *
 * <p>Order: -100 (runs early in filter chain, before logging filter)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class CorrelationIdFilter extends OncePerRequestFilter {

  /** Header name for correlation ID. */
  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

  /** Key for storing correlation ID in MDC (Mapped Diagnostic Context). */
  public static final String CORRELATION_ID_MDC_KEY = "correlationId";

  /**
   * Processes the request by resolving a safe correlation ID and storing it in MDC.
   *
   * <p>This method performs the following operations:
   *
   * <ul>
   *   <li>Normalizes a valid request correlation ID or generates a new one
   *   <li>Stores the correlation ID in MDC for use in log statements
   *   <li>Adds the correlation ID to the response header
   *   <li>Processes the request through the filter chain
   *   <li>Cleans up MDC after request processing
   * </ul>
   *
   * @param request the HTTP request
   * @param response the HTTP response
   * @param filterChain the filter chain to continue processing
   * @throws ServletException if a servlet error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    var correlationId =
        CorrelationIdResolver.resolveOrGenerate(request.getHeader(CORRELATION_ID_HEADER));

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
}
