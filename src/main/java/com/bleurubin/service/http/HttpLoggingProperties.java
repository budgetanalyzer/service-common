package com.bleurubin.service.http;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for HTTP request/response logging.
 *
 * <p>Usage in application.yml:
 *
 * <pre>
 * service:
 *   http-logging:
 *     enabled: true
 *     log-level: DEBUG
 *     include-request-body: true
 *     include-response-body: true
 *     max-body-size: 10000
 *     exclude-patterns:
 *       - /actuator/**
 *       - /swagger-ui/**
 * </pre>
 */
@ConfigurationProperties(prefix = "service.http-logging")
public class HttpLoggingProperties {

  /** Enable/disable HTTP logging filter */
  private boolean enabled = false;

  /** Log level for HTTP logging (DEBUG, INFO, WARN, ERROR) */
  private String logLevel = "DEBUG";

  /** Include request body in logs */
  private boolean includeRequestBody = true;

  /** Include response body in logs */
  private boolean includeResponseBody = true;

  /** Include request headers in logs */
  private boolean includeRequestHeaders = true;

  /** Include response headers in logs */
  private boolean includeResponseHeaders = true;

  /** Include query parameters in logs */
  private boolean includeQueryParams = true;

  /** Include client IP address in logs */
  private boolean includeClientIp = true;

  /** Maximum request/response body size to log (bytes). Bodies larger than this are truncated. */
  private int maxBodySize = 10000; // 10KB default

  /** URL patterns to exclude from logging (Ant-style patterns) */
  private List<String> excludePatterns = new ArrayList<>();

  /** URL patterns to explicitly include (overrides excludePatterns) */
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

  /** Log only requests that result in errors (4xx, 5xx status codes) */
  private boolean logErrorsOnly = false;

  // Getters and Setters

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getLogLevel() {
    return logLevel;
  }

  public void setLogLevel(String logLevel) {
    this.logLevel = logLevel;
  }

  public boolean isIncludeRequestBody() {
    return includeRequestBody;
  }

  public void setIncludeRequestBody(boolean includeRequestBody) {
    this.includeRequestBody = includeRequestBody;
  }

  public boolean isIncludeResponseBody() {
    return includeResponseBody;
  }

  public void setIncludeResponseBody(boolean includeResponseBody) {
    this.includeResponseBody = includeResponseBody;
  }

  public boolean isIncludeRequestHeaders() {
    return includeRequestHeaders;
  }

  public void setIncludeRequestHeaders(boolean includeRequestHeaders) {
    this.includeRequestHeaders = includeRequestHeaders;
  }

  public boolean isIncludeResponseHeaders() {
    return includeResponseHeaders;
  }

  public void setIncludeResponseHeaders(boolean includeResponseHeaders) {
    this.includeResponseHeaders = includeResponseHeaders;
  }

  public boolean isIncludeQueryParams() {
    return includeQueryParams;
  }

  public void setIncludeQueryParams(boolean includeQueryParams) {
    this.includeQueryParams = includeQueryParams;
  }

  public boolean isIncludeClientIp() {
    return includeClientIp;
  }

  public void setIncludeClientIp(boolean includeClientIp) {
    this.includeClientIp = includeClientIp;
  }

  public int getMaxBodySize() {
    return maxBodySize;
  }

  public void setMaxBodySize(int maxBodySize) {
    this.maxBodySize = maxBodySize;
  }

  public List<String> getExcludePatterns() {
    return excludePatterns;
  }

  public void setExcludePatterns(List<String> excludePatterns) {
    this.excludePatterns = excludePatterns;
  }

  public List<String> getIncludePatterns() {
    return includePatterns;
  }

  public void setIncludePatterns(List<String> includePatterns) {
    this.includePatterns = includePatterns;
  }

  public List<String> getSensitiveHeaders() {
    return sensitiveHeaders;
  }

  public void setSensitiveHeaders(List<String> sensitiveHeaders) {
    this.sensitiveHeaders = sensitiveHeaders;
  }

  public boolean isLogErrorsOnly() {
    return logErrorsOnly;
  }

  public void setLogErrorsOnly(boolean logErrorsOnly) {
    this.logErrorsOnly = logErrorsOnly;
  }
}
