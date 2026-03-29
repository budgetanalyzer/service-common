package org.budgetanalyzer.service.servlet.http;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.budgetanalyzer.service.config.HttpLoggingProperties;

/**
 * Configuration for HTTP logging filters in servlet-based applications.
 *
 * <p>This configuration provides:
 *
 * <ul>
 *   <li>{@link CorrelationIdFilter} - Always enabled - Manages correlation IDs for distributed
 *       tracing
 *   <li>{@link HttpLoggingFilter} - Opt-in - Logs HTTP requests/responses with configurable detail
 * </ul>
 *
 * <p>To enable HTTP logging, add to application.yml:
 *
 * <pre>
 * budgetanalyzer:
 *   service:
 *     http-logging:
 *       enabled: true
 *       # Body logging only emits text payloads.
 *       # JSON and form secrets are redacted; multipart, binary, and compressed bodies are omitted.
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(HttpLoggingProperties.class)
public class HttpLoggingConfig {

  /**
   * Correlation ID filter - always enabled for servlet applications.
   *
   * @return configured CorrelationIdFilter
   */
  @Bean
  public CorrelationIdFilter correlationIdFilter() {
    return new CorrelationIdFilter();
  }

  /**
   * HTTP logging filter - enabled via property.
   *
   * @param properties HTTP logging configuration
   * @return configured HttpLoggingFilter
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "budgetanalyzer.service.http-logging",
      name = "enabled",
      havingValue = "true")
  public HttpLoggingFilter httpLoggingFilter(HttpLoggingProperties properties) {
    return new HttpLoggingFilter(properties);
  }
}
