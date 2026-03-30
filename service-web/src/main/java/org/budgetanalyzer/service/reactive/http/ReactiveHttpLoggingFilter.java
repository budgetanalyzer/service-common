package org.budgetanalyzer.service.reactive.http;

import java.util.LinkedHashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

import org.budgetanalyzer.core.logging.HttpBodyLogSanitizer;
import org.budgetanalyzer.core.logging.HttpLogFormatter;
import org.budgetanalyzer.core.logging.QueryParamSanitizer;
import org.budgetanalyzer.core.logging.SensitiveHeaderMasker;
import org.budgetanalyzer.service.config.HttpLoggingProperties;

/**
 * Reactive filter for HTTP request/response logging.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Logs request method, URI, headers, query parameters, and body
 *   <li>Logs response status, headers, and body
 *   <li>Redacts common secret fields in structured text bodies and masks sensitive headers
 *   <li>Suppresses multipart, binary, and compressed bodies
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

  private final HttpLoggingProperties httpLoggingProperties;
  private final QueryParamSanitizer queryParamSanitizer;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  /**
   * Constructs a ReactiveHttpLoggingFilter with the specified configuration httpLoggingProperties.
   *
   * @param httpLoggingProperties the HTTP logging configuration properties
   */
  public ReactiveHttpLoggingFilter(HttpLoggingProperties httpLoggingProperties) {
    this.httpLoggingProperties = httpLoggingProperties;
    this.queryParamSanitizer =
        new QueryParamSanitizer(httpLoggingProperties.getSensitiveQueryParams());
  }

  /**
   * Logs HTTP request and response details with header masking and body sanitization.
   *
   * @param exchange the server web exchange
   * @param chain the filter chain to continue processing
   * @return Mono that completes when the request is processed
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!httpLoggingProperties.isEnabled()) {
      return chain.filter(exchange);
    }

    // Skip if request should not be logged (health checks, excluded paths)
    if (shouldSkipLogging(exchange.getRequest())) {
      return chain.filter(exchange);
    }

    var startTime = System.currentTimeMillis();
    var requestToLog = exchange.getRequest();
    var responseToLog = exchange.getResponse();
    var exchangeBuilder = exchange.mutate();

    if (httpLoggingProperties.isIncludeRequestBody()) {
      requestToLog =
          new CachedBodyServerHttpRequestDecorator(
              exchange.getRequest(), httpLoggingProperties.getMaxBodySize());
      exchangeBuilder.request(requestToLog);
    }

    if (httpLoggingProperties.isIncludeResponseBody()) {
      responseToLog =
          new CachedBodyServerHttpResponseDecorator(
              exchange.getResponse(), httpLoggingProperties.getMaxBodySize());
      exchangeBuilder.response(responseToLog);
    }

    var decoratedExchange = exchangeBuilder.build();
    var requestForLogging = requestToLog;
    var responseForLogging = responseToLog;

    return chain
        .filter(decoratedExchange)
        .doFinally(
            signalType -> {
              var duration = System.currentTimeMillis() - startTime;
              logRequest(requestForLogging);
              logResponse(responseForLogging, duration);
            });
  }

  /**
   * Logs request details.
   *
   * @param request the request to log
   */
  private void logRequest(ServerHttpRequest request) {
    try {
      var details = new LinkedHashMap<String, Object>();
      details.put("method", request.getMethod().name());
      details.put("uri", request.getURI().getPath());

      if (httpLoggingProperties.isIncludeQueryParams() && request.getURI().getQuery() != null) {
        details.put("queryString", queryParamSanitizer.sanitize(request.getURI().getQuery()));
      }

      if (httpLoggingProperties.isIncludeClientIp() && request.getRemoteAddress() != null) {
        var inetAddress = request.getRemoteAddress().getAddress();
        if (inetAddress != null) {
          details.put("clientIp", inetAddress.getHostAddress());
        }
      }

      if (httpLoggingProperties.isIncludeRequestHeaders()) {
        details.put(
            "headers",
            extractHeaders(request.getHeaders(), httpLoggingProperties.getSensitiveHeaders()));
      }

      String requestBody = null;
      if (httpLoggingProperties.isIncludeRequestBody()
          && request instanceof CachedBodyServerHttpRequestDecorator requestDecorator) {
        requestBody =
            HttpBodyLogSanitizer.prepareBodyForLogging(
                requestDecorator.getCachedBodyPrefix(),
                requestDecorator.getObservedBodySize(),
                request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                request.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING),
                getCharacterEncoding(request.getHeaders()));
      }

      var message = HttpLogFormatter.formatLogMessage("HTTP Request", details, requestBody);
      logAtConfiguredLevel(message);

    } catch (Exception e) {
      log.warn("Failed to log HTTP request: {}", e.getMessage(), e);
    }
  }

  /**
   * Logs response details.
   *
   * @param response the response to log
   * @param duration request processing duration in milliseconds
   */
  private void logResponse(ServerHttpResponse response, long duration) {
    try {
      var statusCode = response.getStatusCode();
      if (statusCode == null) {
        return;
      }

      // Check if we should only log errors
      if (httpLoggingProperties.isLogErrorsOnly() && statusCode.value() < 400) {
        return;
      }

      var details = new LinkedHashMap<String, Object>();
      details.put("status", statusCode.value());
      details.put("durationMs", duration);

      if (httpLoggingProperties.isIncludeResponseHeaders()) {
        details.put(
            "headers",
            extractHeaders(response.getHeaders(), httpLoggingProperties.getSensitiveHeaders()));
      }

      String responseBody = null;
      if (httpLoggingProperties.isIncludeResponseBody()
          && response instanceof CachedBodyServerHttpResponseDecorator responseDecorator) {
        responseBody =
            HttpBodyLogSanitizer.prepareBodyForLogging(
                responseDecorator.getCachedBodyPrefix(),
                responseDecorator.getObservedBodySize(),
                response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING),
                getCharacterEncoding(response.getHeaders()));
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

  private LinkedHashMap<String, String> extractHeaders(
      HttpHeaders headers, List<String> sensitiveHeaders) {
    var sanitizedHeaders = new LinkedHashMap<String, String>();

    headers.forEach(
        (headerName, headerValues) -> {
          var headerValue = String.join(",", headerValues);
          if (SensitiveHeaderMasker.isSensitive(headerName, sensitiveHeaders)) {
            sanitizedHeaders.put(headerName, SensitiveHeaderMasker.mask(headerValue));
          } else {
            sanitizedHeaders.put(headerName, headerValue);
          }
        });

    return sanitizedHeaders;
  }

  /**
   * Logs message at the configured log level.
   *
   * @param message the message to log
   */
  private void logAtConfiguredLevel(String message) {
    var level = httpLoggingProperties.getLogLevel().toUpperCase();

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
   * Resolves the configured charset from headers or defaults to UTF-8.
   *
   * @param headers request or response headers
   * @return charset name for body decoding
   */
  private String getCharacterEncoding(HttpHeaders headers) {
    var contentType = headers.getContentType();
    if (contentType != null && contentType.getCharset() != null) {
      return contentType.getCharset().name();
    }
    return null;
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
    if (httpLoggingProperties.isHealthCheckAgent(userAgent)) {
      return true;
    }

    // Get path for pattern matching
    var path = request.getURI().getPath();

    // Check explicit include patterns first
    if (!httpLoggingProperties.getIncludePatterns().isEmpty()) {
      var included =
          httpLoggingProperties.getIncludePatterns().stream()
              .anyMatch(pattern -> pathMatcher.match(pattern, path));

      if (!included) {
        return true; // Not in include list, skip
      }
    }

    // Check exclude patterns
    if (!httpLoggingProperties.getExcludePatterns().isEmpty()) {
      var excluded =
          httpLoggingProperties.getExcludePatterns().stream()
              .anyMatch(pattern -> pathMatcher.match(pattern, path));

      if (excluded) {
        return true; // Explicitly excluded, skip
      }
    }

    return false;
  }
}
