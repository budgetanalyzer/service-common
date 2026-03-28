package org.budgetanalyzer.service.reactive.http;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import org.budgetanalyzer.core.logging.CorrelationIdResolver;

/**
 * Reactive filter for managing safe correlation IDs in distributed tracing.
 *
 * <p>Unlike servlet-based filters that use MDC (thread-local), reactive filters store the
 * correlation ID in Reactor Context which propagates through the reactive chain. Malformed inbound
 * values are discarded and replaced with a new generated ID.
 *
 * <p>Order: 100 (runs before all other filters)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class ReactiveCorrelationIdFilter implements WebFilter {

  /** Header name for correlation ID. */
  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

  /** Key for storing correlation ID in Reactor Context. */
  public static final String CORRELATION_ID_CONTEXT_KEY = "correlationId";

  /**
   * Processes the request by resolving a safe correlation ID.
   *
   * <p>The correlation ID is:
   *
   * <ul>
   *   <li>Normalized from the request header or generated when absent or malformed
   *   <li>Added to response headers for client-side tracing
   *   <li>Stored in Reactor Context for use in downstream reactive operations
   * </ul>
   *
   * @param exchange the server web exchange
   * @param chain the filter chain to continue processing
   * @return Mono that completes when the request is processed
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    var correlationId =
        CorrelationIdResolver.resolveOrGenerate(
            exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER));

    // Add to response headers
    exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);

    // Store in Reactor Context for logging
    return chain
        .filter(exchange)
        .contextWrite(Context.of(CORRELATION_ID_CONTEXT_KEY, correlationId));
  }
}
