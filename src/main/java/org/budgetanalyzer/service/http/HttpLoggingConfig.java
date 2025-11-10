package org.budgetanalyzer.service.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for HTTP request/response logging.
 *
 * <p>This configuration is automatically discovered by Spring Boot via component scanning. It
 * provides:
 *
 * <ul>
 *   <li>{@link CorrelationIdFilter} - Always enabled for web applications
 *   <li>{@link HttpLoggingFilter} - Conditionally enabled based on configuration
 * </ul>
 *
 * <p>Enable HTTP logging in application.yml:
 *
 * <pre>
 * bleurubin:
 *   service:
 *     http-logging:
 *       enabled: true
 * </pre>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(HttpLoggingProperties.class)
public class HttpLoggingConfig {

  private static final Logger log = LoggerFactory.getLogger(HttpLoggingConfig.class);

  /**
   * Registers the correlation ID filter.
   *
   * <p>This filter is always enabled for web applications to ensure all requests have a correlation
   * ID for distributed tracing.
   *
   * @return CorrelationIdFilter bean
   */
  @Bean
  public CorrelationIdFilter correlationIdFilter() {
    log.info("Registering CorrelationIdFilter for HTTP request correlation tracking");
    return new CorrelationIdFilter();
  }

  /**
   * Registers the HTTP logging filter.
   *
   * <p>This filter is conditionally enabled based on the {@code
   * bleurubin.service.http-logging.enabled} property.
   *
   * @param properties HTTP logging configuration properties
   * @return HttpLoggingFilter bean
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "bleurubin.service.http-logging",
      name = "enabled",
      havingValue = "true")
  public HttpLoggingFilter httpLoggingFilter(HttpLoggingProperties properties) {
    log.info(
        "Registering HttpLoggingFilter with log level: {}, max body size: {} bytes",
        properties.getLogLevel(),
        properties.getMaxBodySize());

    if (!properties.getExcludePatterns().isEmpty()) {
      log.info("HTTP logging exclude patterns: {}", properties.getExcludePatterns());
    }

    if (!properties.getIncludePatterns().isEmpty()) {
      log.info("HTTP logging include patterns: {}", properties.getIncludePatterns());
    }

    return new HttpLoggingFilter(properties);
  }
}
