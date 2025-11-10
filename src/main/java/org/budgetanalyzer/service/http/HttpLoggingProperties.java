package org.budgetanalyzer.service.http;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for HTTP request/response logging.
 *
 * <p>Usage in application.yml:
 *
 * <pre>
 * bleurubin:
 *   service:
 *     http-logging:
 *       enabled: true
 *       log-level: DEBUG
 *       include-request-body: true
 *       include-response-body: true
 *       max-body-size: 10000
 *       exclude-patterns:
 *         - /actuator/**
 *         - /swagger-ui/**
 * </pre>
 */
@ConfigurationProperties(prefix = "bleurubin.service.http-logging")
public class HttpLoggingProperties {

  /** Enable/disable HTTP logging filter. */
  private boolean enabled = false;

  /** Log level for HTTP logging (DEBUG, INFO, WARN, ERROR). */
  private String logLevel = "DEBUG";

  /** Include request body in logs. */
  private boolean includeRequestBody = true;

  /** Include response body in logs. */
  private boolean includeResponseBody = true;

  /** Include request headers in logs. */
  private boolean includeRequestHeaders = true;

  /** Include response headers in logs. */
  private boolean includeResponseHeaders = true;

  /** Include query parameters in logs. */
  private boolean includeQueryParams = true;

  /** Include client IP address in logs. */
  private boolean includeClientIp = true;

  /** Maximum request/response body size to log (bytes). Bodies larger than this are truncated. */
  private int maxBodySize = 10000; // 10KB default

  /** URL patterns to exclude from logging (Ant-style patterns). */
  private List<String> excludePatterns = new ArrayList<>();

  /** URL patterns to explicitly include (overrides excludePatterns). */
  private List<String> includePatterns = new ArrayList<>();

  /**
   * Header names to redact/mask in logs (case-insensitive). Default includes common sensitive
   * headers.
   */
  private List<String> sensitiveHeaders =
      List.of(
          "Authorization",
          "Cookie",
          "Set-Cookie",
          "X-API-Key",
          "X-Auth-Token",
          "Proxy-Authorization",
          "WWW-Authenticate");

  /** Log only requests that result in errors (4xx, 5xx status codes). */
  private boolean logErrorsOnly = false;

  // Getters and Setters

  /**
   * Checks whether HTTP logging is enabled.
   *
   * @return true if enabled, false otherwise
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets whether HTTP logging is enabled.
   *
   * @param enabled true to enable, false to disable
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Gets the log level for HTTP logging.
   *
   * @return the log level (DEBUG, INFO, WARN, ERROR)
   */
  public String getLogLevel() {
    return logLevel;
  }

  /**
   * Sets the log level for HTTP logging.
   *
   * @param logLevel the log level (DEBUG, INFO, WARN, ERROR)
   */
  public void setLogLevel(String logLevel) {
    this.logLevel = logLevel;
  }

  /**
   * Checks whether request body logging is enabled.
   *
   * @return true if enabled, false otherwise
   */
  public boolean isIncludeRequestBody() {
    return includeRequestBody;
  }

  /**
   * Sets whether request body logging is enabled.
   *
   * @param includeRequestBody true to enable, false to disable
   */
  public void setIncludeRequestBody(boolean includeRequestBody) {
    this.includeRequestBody = includeRequestBody;
  }

  /**
   * Checks whether response body logging is enabled.
   *
   * @return true if enabled, false otherwise
   */
  public boolean isIncludeResponseBody() {
    return includeResponseBody;
  }

  /**
   * Sets whether response body logging is enabled.
   *
   * @param includeResponseBody true to enable, false to disable
   */
  public void setIncludeResponseBody(boolean includeResponseBody) {
    this.includeResponseBody = includeResponseBody;
  }

  /**
   * Checks whether request headers logging is enabled.
   *
   * @return true if enabled, false otherwise
   */
  public boolean isIncludeRequestHeaders() {
    return includeRequestHeaders;
  }

  /**
   * Sets whether request headers logging is enabled.
   *
   * @param includeRequestHeaders true to enable, false to disable
   */
  public void setIncludeRequestHeaders(boolean includeRequestHeaders) {
    this.includeRequestHeaders = includeRequestHeaders;
  }

  /**
   * Checks whether response headers logging is enabled.
   *
   * @return true if enabled, false otherwise
   */
  public boolean isIncludeResponseHeaders() {
    return includeResponseHeaders;
  }

  /**
   * Sets whether response headers logging is enabled.
   *
   * @param includeResponseHeaders true to enable, false to disable
   */
  public void setIncludeResponseHeaders(boolean includeResponseHeaders) {
    this.includeResponseHeaders = includeResponseHeaders;
  }

  /**
   * Checks whether query parameters logging is enabled.
   *
   * @return true if enabled, false otherwise
   */
  public boolean isIncludeQueryParams() {
    return includeQueryParams;
  }

  /**
   * Sets whether query parameters logging is enabled.
   *
   * @param includeQueryParams true to enable, false to disable
   */
  public void setIncludeQueryParams(boolean includeQueryParams) {
    this.includeQueryParams = includeQueryParams;
  }

  /**
   * Checks whether client IP logging is enabled.
   *
   * @return true if enabled, false otherwise
   */
  public boolean isIncludeClientIp() {
    return includeClientIp;
  }

  /**
   * Sets whether client IP logging is enabled.
   *
   * @param includeClientIp true to enable, false to disable
   */
  public void setIncludeClientIp(boolean includeClientIp) {
    this.includeClientIp = includeClientIp;
  }

  /**
   * Gets the maximum body size to log in bytes.
   *
   * @return the maximum body size
   */
  public int getMaxBodySize() {
    return maxBodySize;
  }

  /**
   * Sets the maximum body size to log in bytes.
   *
   * @param maxBodySize the maximum body size
   */
  public void setMaxBodySize(int maxBodySize) {
    this.maxBodySize = maxBodySize;
  }

  /**
   * Gets the URL patterns to exclude from logging.
   *
   * @return the exclude patterns
   */
  public List<String> getExcludePatterns() {
    return excludePatterns;
  }

  /**
   * Sets the URL patterns to exclude from logging.
   *
   * @param excludePatterns the exclude patterns
   */
  public void setExcludePatterns(List<String> excludePatterns) {
    this.excludePatterns = excludePatterns;
  }

  /**
   * Gets the URL patterns to explicitly include in logging.
   *
   * @return the include patterns
   */
  public List<String> getIncludePatterns() {
    return includePatterns;
  }

  /**
   * Sets the URL patterns to explicitly include in logging.
   *
   * @param includePatterns the include patterns
   */
  public void setIncludePatterns(List<String> includePatterns) {
    this.includePatterns = includePatterns;
  }

  /**
   * Gets the header names to redact/mask in logs.
   *
   * @return the sensitive header names
   */
  public List<String> getSensitiveHeaders() {
    return sensitiveHeaders;
  }

  /**
   * Sets the header names to redact/mask in logs.
   *
   * @param sensitiveHeaders the sensitive header names
   */
  public void setSensitiveHeaders(List<String> sensitiveHeaders) {
    this.sensitiveHeaders = sensitiveHeaders;
  }

  /**
   * Checks whether to log only errors (4xx, 5xx status codes).
   *
   * @return true if only errors should be logged, false otherwise
   */
  public boolean isLogErrorsOnly() {
    return logErrorsOnly;
  }

  /**
   * Sets whether to log only errors (4xx, 5xx status codes).
   *
   * @param logErrorsOnly true to log only errors, false to log all requests
   */
  public void setLogErrorsOnly(boolean logErrorsOnly) {
    this.logErrorsOnly = logErrorsOnly;
  }
}
