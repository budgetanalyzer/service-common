package org.budgetanalyzer.service.reactive.http;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

import org.budgetanalyzer.core.logging.HttpLogFormatter;
import org.budgetanalyzer.service.config.HttpLoggingProperties;

/**
 * Reactive filter for HTTP request/response logging.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Logs request method, URI, headers, query parameters, and body
 *   <li>Logs response status, headers, and body
 *   <li>Automatic sensitive data masking
 *   <li>Configurable body size limits
 *   <li>Path-based filtering (include/exclude patterns)
 * </ul>
 *
 * <p>Order: 150 (runs after ReactiveCorrelationIdFilter)
 *
 * <p>Configuration via {@link HttpLoggingProperties}
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 150)
public class ReactiveHttpLoggingFilter implements WebFilter {

  private static final Logger log = LoggerFactory.getLogger(ReactiveHttpLoggingFilter.class);

  /** Content-Encoding values that indicate compressed content. */
  private static final Set<String> COMPRESSED_ENCODINGS =
      Set.of("gzip", "deflate", "br", "compress");

  private final HttpLoggingProperties properties;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  /**
   * Constructs a ReactiveHttpLoggingFilter with the specified configuration properties.
   *
   * @param properties the HTTP logging configuration properties
   */
  public ReactiveHttpLoggingFilter(HttpLoggingProperties properties) {
    this.properties = properties;
  }

  /**
   * Logs HTTP request and response details with sensitive data masking.
   *
   * @param exchange the server web exchange
   * @param chain the filter chain to continue processing
   * @return Mono that completes when the request is processed
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!properties.isEnabled()) {
      return chain.filter(exchange);
    }

    // Skip if request should not be logged (health checks, excluded paths)
    if (shouldSkipLogging(exchange.getRequest())) {
      return chain.filter(exchange);
    }

    var startTime = System.currentTimeMillis();

    // Decorate request/response to cache bodies
    var decoratedRequest = new CachedBodyServerHttpRequestDecorator(exchange.getRequest());
    var decoratedResponse =
        new CachedBodyServerHttpResponseDecorator(
            exchange.getResponse(), properties.getMaxBodySize());

    var decoratedExchange =
        exchange.mutate().request(decoratedRequest).response(decoratedResponse).build();

    // Log request
    return logRequest(decoratedRequest)
        .then(chain.filter(decoratedExchange))
        .doFinally(
            signalType -> {
              var duration = System.currentTimeMillis() - startTime;
              logResponse(decoratedResponse, duration);
            });
  }

  /**
   * Logs request details.
   *
   * @param request the cached request decorator
   * @return Mono that completes after logging
   */
  private Mono<Void> logRequest(CachedBodyServerHttpRequestDecorator request) {
    try {
      Map<String, Object> details = new HashMap<>();
      details.put("method", request.getMethod().name());
      details.put("uri", request.getURI().toString());

      if (properties.isIncludeQueryParams() && request.getURI().getQuery() != null) {
        details.put("queryString", request.getURI().getQuery());
      }

      if (properties.isIncludeClientIp() && request.getRemoteAddress() != null) {
        var inetAddress = request.getRemoteAddress().getAddress();
        if (inetAddress != null) {
          details.put("clientIp", inetAddress.getHostAddress());
        }
      }

      if (properties.isIncludeRequestHeaders()) {
        details.put("headers", request.getHeaders());
      }

      // Log request body if enabled
      if (properties.isIncludeRequestBody()) {
        return request
            .getCachedBodyAsString(properties.getMaxBodySize())
            .doOnNext(
                body -> {
                  var message = HttpLogFormatter.formatLogMessage("HTTP Request", details, body);
                  logAtConfiguredLevel(message);
                })
            .then();
      } else {
        var message = HttpLogFormatter.formatLogMessage("HTTP Request", details, null);
        logAtConfiguredLevel(message);
        return Mono.empty();
      }

    } catch (Exception e) {
      log.warn("Failed to log HTTP request: {}", e.getMessage(), e);
      return Mono.empty();
    }
  }

  /**
   * Logs response details.
   *
   * @param response the cached response decorator
   * @param duration request processing duration in milliseconds
   */
  private void logResponse(CachedBodyServerHttpResponseDecorator response, long duration) {
    try {
      var statusCode = response.getStatusCode();
      if (statusCode == null) {
        return;
      }

      // Check if we should only log errors
      if (properties.isLogErrorsOnly() && statusCode.value() < 400) {
        return;
      }

      Map<String, Object> details = new HashMap<>();
      details.put("status", statusCode.value());
      details.put("durationMs", duration);

      if (properties.isIncludeResponseHeaders()) {
        details.put("headers", response.getHeaders());
      }

      String responseBody = null;
      if (properties.isIncludeResponseBody()) {
        // Check if response is compressed
        var contentEncoding = response.getHeaders().getFirst("Content-Encoding");
        if (isCompressed(contentEncoding)) {
          responseBody =
              "[compressed: " + contentEncoding + ", " + response.getCachedBodySize() + " bytes]";
        } else {
          responseBody = response.getCachedBody();
        }
      }

      var message = HttpLogFormatter.formatLogMessage("HTTP Response", details, responseBody);

      // Log errors at higher level
      if (statusCode.value() >= 500) {
        log.error(message);
      } else if (statusCode.value() >= 400) {
        log.warn(message);
      } else {
        logAtConfiguredLevel(message);
      }

    } catch (Exception e) {
      log.warn("Failed to log HTTP response: {}", e.getMessage(), e);
    }
  }

  /**
   * Logs message at the configured log level.
   *
   * @param message the message to log
   */
  private void logAtConfiguredLevel(String message) {
    var level = properties.getLogLevel().toUpperCase();

    switch (level) {
      case "TRACE":
        log.trace(message);
        break;
      case "DEBUG":
        log.debug(message);
        break;
      case "INFO":
        log.info(message);
        break;
      case "WARN":
        log.warn(message);
        break;
      case "ERROR":
        log.error(message);
        break;
      default:
        log.debug(message);
    }
  }

  /**
   * Checks if the Content-Encoding indicates compressed content.
   *
   * @param contentEncoding the Content-Encoding header value
   * @return true if the content is compressed
   */
  private boolean isCompressed(String contentEncoding) {
    if (contentEncoding == null || contentEncoding.isEmpty()) {
      return false;
    }

    // Handle multiple encodings (e.g., "gzip, deflate") by checking each
    var encodings = contentEncoding.toLowerCase().split(",");
    for (String encoding : encodings) {
      if (COMPRESSED_ENCODINGS.contains(encoding.trim())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Determines if logging should be skipped for this request.
   *
   * @param request the server HTTP request
   * @return true if logging should be skipped
   */
  private boolean shouldSkipLogging(ServerHttpRequest request) {
    // Skip health check agents (Kubernetes probes, AWS ELB, GCP health checks)
    var userAgent = request.getHeaders().getFirst("User-Agent");
    if (properties.isHealthCheckAgent(userAgent)) {
      return true;
    }

    // Get path for pattern matching
    var path = request.getURI().getPath();

    // Check explicit include patterns first
    if (!properties.getIncludePatterns().isEmpty()) {
      var included =
          properties.getIncludePatterns().stream()
              .anyMatch(pattern -> pathMatcher.match(pattern, path));

      if (!included) {
        return true; // Not in include list, skip
      }
    }

    // Check exclude patterns
    if (!properties.getExcludePatterns().isEmpty()) {
      var excluded =
          properties.getExcludePatterns().stream()
              .anyMatch(pattern -> pathMatcher.match(pattern, path));

      if (excluded) {
        return true; // Explicitly excluded, skip
      }
    }

    return false;
  }
}
