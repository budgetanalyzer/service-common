package org.budgetanalyzer.core.logging;

import java.util.Map;

/**
 * Utility for formatting HTTP request/response log messages.
 *
 * <p>Provides consistent formatting for structured HTTP logs with details and optional body
 * content.
 */
public final class HttpLogFormatter {

  private HttpLogFormatter() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Formats a log message with prefix, details map, and optional pre-sanitized body.
   *
   * @param prefix message prefix (e.g., "HTTP Request")
   * @param details structured details as key-value pairs
   * @param body optional body content that has already been sanitized for logging
   * @return formatted log message
   */
  public static String formatLogMessage(String prefix, Map<String, Object> details, String body) {
    var sb = new StringBuilder();
    sb.append(prefix).append(" - ");
    sb.append(SafeLogger.toJson(details));

    if (body != null && !body.isEmpty()) {
      sb.append("\nBody: ").append(body);
    }

    return sb.toString();
  }
}
