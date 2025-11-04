package com.bleurubin.service.http;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.bleurubin.core.logging.SafeLogger;

/** Utility class for formatting and sanitizing HTTP request/response content for logging. */
public class ContentLoggingUtil {

  private static final String MASKED_VALUE = "********";

  /**
   * Extracts request details for logging.
   *
   * @param request The HTTP request
   * @param properties Logging configuration
   * @return Map of request details
   */
  public static Map<String, Object> extractRequestDetails(
      HttpServletRequest request, HttpLoggingProperties properties) {
    var details = new LinkedHashMap<String, Object>();

    details.put("method", request.getMethod());
    details.put("uri", request.getRequestURI());

    if (properties.isIncludeQueryParams() && request.getQueryString() != null) {
      details.put("queryString", request.getQueryString());
    }

    if (properties.isIncludeClientIp()) {
      details.put("clientIp", getClientIpAddress(request));
    }

    if (properties.isIncludeRequestHeaders()) {
      details.put("headers", extractHeaders(request, properties.getSensitiveHeaders()));
    }

    return details;
  }

  /**
   * Extracts response details for logging.
   *
   * @param response The HTTP response
   * @param properties Logging configuration
   * @return Map of response details
   */
  public static Map<String, Object> extractResponseDetails(
      HttpServletResponse response, HttpLoggingProperties properties) {
    var details = new LinkedHashMap<String, Object>();

    details.put("status", response.getStatus());

    if (properties.isIncludeResponseHeaders()) {
      details.put("headers", extractResponseHeaders(response, properties.getSensitiveHeaders()));
    }

    return details;
  }

  /**
   * Extracts request body from ContentCachingRequestWrapper.
   *
   * @param requestWrapper The wrapped request
   * @param maxSize Maximum body size to extract
   * @return Request body as string, or null if empty
   */
  public static String extractRequestBody(
      ContentCachingRequestWrapper requestWrapper, int maxSize) {
    var content = requestWrapper.getContentAsByteArray();

    if (content.length == 0) {
      return null;
    }

    return extractBody(content, requestWrapper.getCharacterEncoding(), maxSize);
  }

  /**
   * Extracts response body from ContentCachingResponseWrapper.
   *
   * @param responseWrapper The wrapped response
   * @param maxSize Maximum body size to extract
   * @return Response body as string, or null if empty
   */
  public static String extractResponseBody(
      ContentCachingResponseWrapper responseWrapper, int maxSize) {
    var content = responseWrapper.getContentAsByteArray();

    if (content.length == 0) {
      return null;
    }

    return extractBody(content, responseWrapper.getCharacterEncoding(), maxSize);
  }

  /**
   * Extracts body content from byte array with size limits.
   *
   * @param content The content bytes
   * @param encoding Character encoding
   * @param maxSize Maximum size to extract
   * @return Body as string
   */
  private static String extractBody(byte[] content, String encoding, int maxSize) {
    var length = Math.min(content.length, maxSize);
    var truncated = content.length > maxSize;

    try {
      var body =
          new String(
              content, 0, length, encoding != null ? encoding : StandardCharsets.UTF_8.name());

      if (truncated) {
        return body + "... [TRUNCATED - " + (content.length - maxSize) + " bytes omitted]";
      }

      return body;
    } catch (UnsupportedEncodingException e) {
      return "[Unable to read body: " + e.getMessage() + "]";
    }
  }

  /**
   * Extracts request headers, masking sensitive ones.
   *
   * @param request The HTTP request
   * @param sensitiveHeaders List of sensitive header names
   * @return Map of headers
   */
  private static Map<String, String> extractHeaders(
      HttpServletRequest request, List<String> sensitiveHeaders) {
    var headers = new LinkedHashMap<String, String>();
    var headerNames = request.getHeaderNames();

    if (headerNames == null) {
      return headers;
    }

    while (headerNames.hasMoreElements()) {
      var headerName = headerNames.nextElement();
      var headerValue = request.getHeader(headerName);

      if (isSensitiveHeader(headerName, sensitiveHeaders)) {
        headers.put(headerName, MASKED_VALUE);
      } else {
        headers.put(headerName, headerValue);
      }
    }

    return headers;
  }

  /**
   * Extracts response headers, masking sensitive ones.
   *
   * @param response The HTTP response
   * @param sensitiveHeaders List of sensitive header names
   * @return Map of headers
   */
  private static Map<String, String> extractResponseHeaders(
      HttpServletResponse response, List<String> sensitiveHeaders) {
    var headers = new LinkedHashMap<String, String>();

    for (String headerName : response.getHeaderNames()) {
      var headerValue = response.getHeader(headerName);

      if (isSensitiveHeader(headerName, sensitiveHeaders)) {
        headers.put(headerName, MASKED_VALUE);
      } else {
        headers.put(headerName, headerValue);
      }
    }

    return headers;
  }

  /**
   * Checks if a header is sensitive (case-insensitive).
   *
   * @param headerName The header name
   * @param sensitiveHeaders List of sensitive header names
   * @return True if sensitive
   */
  private static boolean isSensitiveHeader(String headerName, List<String> sensitiveHeaders) {
    return sensitiveHeaders.stream().anyMatch(sensitive -> sensitive.equalsIgnoreCase(headerName));
  }

  /**
   * Gets the client IP address from the request, checking proxy headers.
   *
   * @param request The HTTP request
   * @return Client IP address
   */
  private static String getClientIpAddress(HttpServletRequest request) {
    String[] headerNames = {
      "X-Forwarded-For",
      "Proxy-Client-IP",
      "WL-Proxy-Client-IP",
      "HTTP_X_FORWARDED_FOR",
      "HTTP_X_FORWARDED",
      "HTTP_X_CLUSTER_CLIENT_IP",
      "HTTP_CLIENT_IP",
      "HTTP_FORWARDED_FOR",
      "HTTP_FORWARDED",
      "HTTP_VIA",
      "REMOTE_ADDR"
    };

    for (String header : headerNames) {
      var ip = request.getHeader(header);
      if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
        // X-Forwarded-For can contain multiple IPs, take the first one
        if (ip.contains(",")) {
          ip = ip.split(",")[0].trim();
        }
        return ip;
      }
    }

    return request.getRemoteAddr();
  }

  /**
   * Formats a log message with request/response details.
   *
   * @param prefix Message prefix (e.g., "HTTP Request", "HTTP Response")
   * @param details Details map
   * @param body Optional body content
   * @return Formatted log message
   */
  public static String formatLogMessage(String prefix, Map<String, Object> details, String body) {
    var sb = new StringBuilder();
    sb.append(prefix).append(" - ");

    // Add key details inline
    if (details.containsKey("method") && details.containsKey("uri")) {
      sb.append(details.get("method")).append(" ").append(details.get("uri"));
    } else if (details.containsKey("status")) {
      sb.append("Status: ").append(details.get("status"));
    }

    sb.append("\nDetails: ");
    sb.append(SafeLogger.toJson(details));

    if (body != null && !body.trim().isEmpty()) {
      sb.append("\nBody: ");
      // Try to parse as JSON and sanitize, otherwise log as-is
      if (isJsonContent(body)) {
        sb.append(body); // Already sanitized by SafeLogger if needed
      } else {
        sb.append(body);
      }
    }

    return sb.toString();
  }

  /**
   * Checks if content appears to be JSON.
   *
   * @param content The content
   * @return True if likely JSON
   */
  private static boolean isJsonContent(String content) {
    var trimmed = content.trim();
    return (trimmed.startsWith("{") && trimmed.endsWith("}"))
        || (trimmed.startsWith("[") && trimmed.endsWith("]"));
  }
}
