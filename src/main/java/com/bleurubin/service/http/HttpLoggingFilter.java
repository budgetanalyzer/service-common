package com.bleurubin.service.http;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Filter that logs HTTP request and response details for debugging and audit purposes.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Logs request method, URI, headers, query parameters, and body
 *   <li>Logs response status, headers, and body
 *   <li>Automatic sensitive data masking (headers like Authorization, Cookie)
 *   <li>Configurable body size limits to avoid logging huge payloads
 *   <li>Path-based filtering (include/exclude patterns)
 *   <li>Integration with correlation ID from MDC
 * </ul>
 *
 * <p>Order: -50 (runs after CorrelationIdFilter)
 *
 * <p>Configuration via {@link HttpLoggingProperties}
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 150)
public class HttpLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

  private final HttpLoggingProperties properties;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  public HttpLoggingFilter(HttpLoggingProperties properties) {
    this.properties = properties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // Skip if logging is disabled
    if (!properties.isEnabled()) {
      filterChain.doFilter(request, response);
      return;
    }

    // Skip if path is excluded
    if (shouldSkipLogging(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    // Wrap request and response to enable content caching
    var requestWrapper = new ContentCachingRequestWrapper(request);
    var responseWrapper = new ContentCachingResponseWrapper(response);

    var startTime = System.currentTimeMillis();

    try {
      // Log request before processing
      logRequest(requestWrapper);

      // Process request
      filterChain.doFilter(requestWrapper, responseWrapper);

    } finally {
      var duration = System.currentTimeMillis() - startTime;

      // Log response after processing
      logResponse(responseWrapper, duration);

      // IMPORTANT: Copy cached response content to actual response
      // Without this, the response body will be empty
      responseWrapper.copyBodyToResponse();
    }
  }

  /**
   * Logs request details.
   *
   * @param request The wrapped request
   */
  private void logRequest(ContentCachingRequestWrapper request) {
    try {
      var requestDetails = ContentLoggingUtil.extractRequestDetails(request, properties);

      String requestBody = null;
      if (properties.isIncludeRequestBody() && hasBody(request)) {
        requestBody = ContentLoggingUtil.extractRequestBody(request, properties.getMaxBodySize());
      }

      var logMessage =
          ContentLoggingUtil.formatLogMessage("HTTP Request", requestDetails, requestBody);

      logAtConfiguredLevel(logMessage);

    } catch (Exception e) {
      log.warn("Failed to log HTTP request: {}", e.getMessage(), e);
    }
  }

  /**
   * Logs response details.
   *
   * @param response The wrapped response
   * @param duration Request processing duration in milliseconds
   */
  private void logResponse(ContentCachingResponseWrapper response, long duration) {
    try {
      // Check if we should only log errors
      if (properties.isLogErrorsOnly() && response.getStatus() < 400) {
        return;
      }

      var responseDetails = ContentLoggingUtil.extractResponseDetails(response, properties);

      responseDetails.put("durationMs", duration);

      String responseBody = null;
      if (properties.isIncludeResponseBody()) {
        responseBody =
            ContentLoggingUtil.extractResponseBody(response, properties.getMaxBodySize());
      }

      var logMessage =
          ContentLoggingUtil.formatLogMessage("HTTP Response", responseDetails, responseBody);

      // Log errors at higher level
      if (response.getStatus() >= 500) {
        log.error(logMessage);
      } else if (response.getStatus() >= 400) {
        log.warn(logMessage);
      } else {
        logAtConfiguredLevel(logMessage);
      }

    } catch (Exception e) {
      log.warn("Failed to log HTTP response: {}", e.getMessage(), e);
    }
  }

  /**
   * Logs message at the configured log level.
   *
   * @param message The message to log
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
   * Checks if request has a body (non-GET/DELETE methods typically have bodies).
   *
   * @param request The request
   * @return True if request likely has a body
   */
  private boolean hasBody(HttpServletRequest request) {
    var method = request.getMethod();
    return "POST".equalsIgnoreCase(method)
        || "PUT".equalsIgnoreCase(method)
        || "PATCH".equalsIgnoreCase(method);
  }

  /**
   * Determines if logging should be skipped for this request.
   *
   * @param request The HTTP request
   * @return True if logging should be skipped
   */
  private boolean shouldSkipLogging(HttpServletRequest request) {
    // Use servlet path to exclude context path from pattern matching
    // This allows patterns like "/actuator/**" to work regardless of context path
    var path = request.getServletPath();

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
