package org.budgetanalyzer.service.reactive.http;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.budgetanalyzer.service.config.HttpLoggingProperties;

/**
 * Configuration for HTTP logging filters in reactive applications.
 *
 * <p>This configuration provides:
 *
 * <ul>
 *   <li>{@link ReactiveCorrelationIdFilter} - Always enabled - Manages correlation IDs for
 *       distributed tracing
 *   <li>{@link ReactiveHttpLoggingFilter} - Opt-in - Logs HTTP requests/responses with configurable
 *       detail
 * </ul>
 *
 * <p>To enable HTTP logging, add to application.yml:
 *
 * <pre>
 * budgetanalyzer:
 *   service:
 *     http-logging:
 *       enabled: true
 *       include-request-headers: true
 *       include-response-headers: true
 *       # Body logging is disabled by default and must be enabled explicitly.
 *       # JSON and form secrets are redacted; multipart, binary, and compressed bodies are omitted:
 *       # include-request-body: true
 *       # include-response-body: true
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(HttpLoggingProperties.class)
public class ReactiveHttpLoggingConfig {

  /**
   * Correlation ID filter - always enabled for reactive applications.
   *
   * @return configured ReactiveCorrelationIdFilter
   */
  @Bean
  public ReactiveCorrelationIdFilter reactiveCorrelationIdFilter() {
    return new ReactiveCorrelationIdFilter();
  }

  /**
   * HTTP logging filter - enabled via property.
   *
   * @param properties HTTP logging configuration
   * @return configured ReactiveHttpLoggingFilter
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "budgetanalyzer.service.http-logging",
      name = "enabled",
      havingValue = "true")
  public ReactiveHttpLoggingFilter reactiveHttpLoggingFilter(HttpLoggingProperties properties) {
    return new ReactiveHttpLoggingFilter(properties);
  }
}
