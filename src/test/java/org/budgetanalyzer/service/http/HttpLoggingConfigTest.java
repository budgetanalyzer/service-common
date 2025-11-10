package org.budgetanalyzer.service.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class HttpLoggingConfigTest {

  private final WebApplicationContextRunner webContextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(HttpLoggingConfig.class));

  private final ApplicationContextRunner nonWebContextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(HttpLoggingConfig.class));

  @Test
  void shouldRegisterCorrelationIdFilterInWebApplication() {
    // Arrange & Act
    webContextRunner.run(
        context -> {
          // Assert
          assertTrue(
              context.containsBean("correlationIdFilter"),
              "Should register CorrelationIdFilter bean");
          assertNotNull(
              context.getBean(CorrelationIdFilter.class),
              "Should be able to get CorrelationIdFilter bean");
        });
  }

  @Test
  void shouldNotRegisterCorrelationIdFilterInNonWebApplication() {
    // Arrange & Act
    nonWebContextRunner.run(
        context -> {
          // Assert
          assertFalse(
              context.containsBean("correlationIdFilter"),
              "Should NOT register CorrelationIdFilter in non-web application");
        });
  }

  @Test
  void shouldRegisterHttpLoggingFilterWhenEnabled() {
    // Arrange & Act
    webContextRunner
        .withPropertyValues("budgetanalyzer.service.http-logging.enabled=true")
        .run(
            context -> {
              // Assert
              assertTrue(
                  context.containsBean("httpLoggingFilter"),
                  "Should register HttpLoggingFilter when enabled");
              assertNotNull(
                  context.getBean(HttpLoggingFilter.class),
                  "Should be able to get HttpLoggingFilter bean");
            });
  }

  @Test
  void shouldNotRegisterHttpLoggingFilterWhenDisabled() {
    // Arrange & Act
    webContextRunner
        .withPropertyValues("budgetanalyzer.service.http-logging.enabled=false")
        .run(
            context -> {
              // Assert
              assertFalse(
                  context.containsBean("httpLoggingFilter"),
                  "Should NOT register HttpLoggingFilter when disabled");
            });
  }

  @Test
  void shouldNotRegisterHttpLoggingFilterWhenPropertyNotSet() {
    // Arrange & Act - Don't set the enabled property (defaults to false)
    webContextRunner.run(
        context -> {
          // Assert
          assertFalse(
              context.containsBean("httpLoggingFilter"),
              "Should NOT register HttpLoggingFilter when property not set (defaults to false)");
        });
  }

  @Test
  void shouldRegisterBothFiltersWhenHttpLoggingEnabled() {
    // Arrange & Act
    webContextRunner
        .withPropertyValues("budgetanalyzer.service.http-logging.enabled=true")
        .run(
            context -> {
              // Assert
              assertTrue(
                  context.containsBean("correlationIdFilter"),
                  "Should register CorrelationIdFilter");
              assertTrue(
                  context.containsBean("httpLoggingFilter"), "Should register HttpLoggingFilter");

              assertEquals(
                  2,
                  context.getBeansOfType(CorrelationIdFilter.class).size()
                      + context.getBeansOfType(HttpLoggingFilter.class).size(),
                  "Should have exactly 2 filter beans registered");
            });
  }

  @Test
  void shouldRegisterOnlyCorrelationIdFilterWhenHttpLoggingDisabled() {
    // Arrange & Act
    webContextRunner
        .withPropertyValues("budgetanalyzer.service.http-logging.enabled=false")
        .run(
            context -> {
              // Assert
              assertTrue(
                  context.containsBean("correlationIdFilter"),
                  "Should register CorrelationIdFilter");
              assertFalse(
                  context.containsBean("httpLoggingFilter"),
                  "Should NOT register HttpLoggingFilter");

              assertEquals(
                  1,
                  context.getBeansOfType(CorrelationIdFilter.class).size(),
                  "Should have exactly 1 filter bean (CorrelationIdFilter)");
              assertEquals(
                  0,
                  context.getBeansOfType(HttpLoggingFilter.class).size(),
                  "Should have 0 HttpLoggingFilter beans");
            });
  }

  @Test
  void shouldRegisterHttpLoggingPropertiesBean() {
    // Arrange & Act
    webContextRunner.run(
        context -> {
          // Assert
          assertNotNull(
              context.getBean(HttpLoggingProperties.class),
              "Should be able to get HttpLoggingProperties bean");
        });
  }

  @Test
  void shouldPassPropertiesToHttpLoggingFilter() {
    // Arrange & Act
    webContextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.enabled=true",
            "budgetanalyzer.service.http-logging.log-level=INFO",
            "budgetanalyzer.service.http-logging.max-body-size=5000",
            "budgetanalyzer.service.http-logging.include-request-body=false")
        .run(
            context -> {
              // Assert
              var properties = context.getBean(HttpLoggingProperties.class);
              assertTrue(properties.isEnabled(), "Properties should have enabled=true");
              assertEquals(
                  "INFO", properties.getLogLevel(), "Properties should have log-level=INFO");
              assertEquals(
                  5000, properties.getMaxBodySize(), "Properties should have max-body-size=5000");
              assertFalse(
                  properties.isIncludeRequestBody(),
                  "Properties should have include-request-body=false");

              // Filter should be created with these properties
              assertNotNull(
                  context.getBean(HttpLoggingFilter.class),
                  "HttpLoggingFilter should be created with properties");
            });
  }

  @Test
  void shouldHandleExcludeAndIncludePatterns() {
    // Arrange & Act
    webContextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.enabled=true",
            "budgetanalyzer.service.http-logging.exclude-patterns[0]=/actuator/**",
            "budgetanalyzer.service.http-logging.exclude-patterns[1]=/swagger-ui/**",
            "budgetanalyzer.service.http-logging.include-patterns[0]=/api/**")
        .run(
            context -> {
              // Assert
              var properties = context.getBean(HttpLoggingProperties.class);
              assertEquals(
                  2, properties.getExcludePatterns().size(), "Should have 2 exclude patterns");
              assertEquals(
                  1, properties.getIncludePatterns().size(), "Should have 1 include pattern");
              assertTrue(
                  properties.getExcludePatterns().contains("/actuator/**"),
                  "Should contain /actuator/** exclude pattern");
              assertTrue(
                  properties.getExcludePatterns().contains("/swagger-ui/**"),
                  "Should contain /swagger-ui/** exclude pattern");
              assertTrue(
                  properties.getIncludePatterns().contains("/api/**"),
                  "Should contain /api/** include pattern");
            });
  }

  @Test
  void shouldHandleCustomSensitiveHeaders() {
    // Arrange & Act
    webContextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.enabled=true",
            "budgetanalyzer.service.http-logging.sensitive-headers[0]=X-Custom-Token",
            "budgetanalyzer.service.http-logging.sensitive-headers[1]=X-API-Secret")
        .run(
            context -> {
              // Assert
              var properties = context.getBean(HttpLoggingProperties.class);
              assertEquals(
                  2, properties.getSensitiveHeaders().size(), "Should have 2 sensitive headers");
              assertTrue(
                  properties.getSensitiveHeaders().contains("X-Custom-Token"),
                  "Should contain X-Custom-Token");
              assertTrue(
                  properties.getSensitiveHeaders().contains("X-API-Secret"),
                  "Should contain X-API-Secret");
            });
  }

  @Test
  void shouldHandleLogErrorsOnlyFlag() {
    // Arrange & Act
    webContextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.enabled=true",
            "budgetanalyzer.service.http-logging.log-errors-only=true")
        .run(
            context -> {
              // Assert
              var properties = context.getBean(HttpLoggingProperties.class);
              assertTrue(properties.isLogErrorsOnly(), "Should have log-errors-only=true");
            });
  }

  @Test
  void shouldNotRegisterFiltersInNonWebApplication() {
    // Arrange & Act - Non-web application with http-logging enabled
    nonWebContextRunner
        .withPropertyValues("budgetanalyzer.service.http-logging.enabled=true")
        .run(
            context -> {
              // Assert - No filters should be registered in non-web application
              assertFalse(
                  context.containsBean("correlationIdFilter"),
                  "Should NOT register CorrelationIdFilter in non-web application");
              assertFalse(
                  context.containsBean("httpLoggingFilter"),
                  "Should NOT register HttpLoggingFilter in non-web application");

              // HttpLoggingConfig is conditional on web application,
              // so properties won't be registered either
              assertEquals(
                  0,
                  context.getBeansOfType(HttpLoggingProperties.class).size(),
                  "Should NOT register HttpLoggingProperties in non-web application");
            });
  }

  @Test
  void shouldHandleAllBooleanPropertiesCombinations() {
    // Arrange & Act - Enable all optional features
    webContextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.enabled=true",
            "budgetanalyzer.service.http-logging.include-request-body=true",
            "budgetanalyzer.service.http-logging.include-response-body=true",
            "budgetanalyzer.service.http-logging.include-request-headers=true",
            "budgetanalyzer.service.http-logging.include-response-headers=true",
            "budgetanalyzer.service.http-logging.include-query-params=true",
            "budgetanalyzer.service.http-logging.include-client-ip=true",
            "budgetanalyzer.service.http-logging.log-errors-only=false")
        .run(
            context -> {
              // Assert
              var properties = context.getBean(HttpLoggingProperties.class);
              assertTrue(properties.isEnabled());
              assertTrue(properties.isIncludeRequestBody());
              assertTrue(properties.isIncludeResponseBody());
              assertTrue(properties.isIncludeRequestHeaders());
              assertTrue(properties.isIncludeResponseHeaders());
              assertTrue(properties.isIncludeQueryParams());
              assertTrue(properties.isIncludeClientIp());
              assertFalse(properties.isLogErrorsOnly());
            });
  }

  @Test
  void shouldHandleAllBooleanPropertiesDisabled() {
    // Arrange & Act - Disable all optional features but enable the filter
    webContextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.enabled=true",
            "budgetanalyzer.service.http-logging.include-request-body=false",
            "budgetanalyzer.service.http-logging.include-response-body=false",
            "budgetanalyzer.service.http-logging.include-request-headers=false",
            "budgetanalyzer.service.http-logging.include-response-headers=false",
            "budgetanalyzer.service.http-logging.include-query-params=false",
            "budgetanalyzer.service.http-logging.include-client-ip=false",
            "budgetanalyzer.service.http-logging.log-errors-only=true")
        .run(
            context -> {
              // Assert - Filter should still be created even with minimal logging
              var properties = context.getBean(HttpLoggingProperties.class);
              assertTrue(properties.isEnabled(), "Filter should be enabled");
              assertFalse(properties.isIncludeRequestBody());
              assertFalse(properties.isIncludeResponseBody());
              assertFalse(properties.isIncludeRequestHeaders());
              assertFalse(properties.isIncludeResponseHeaders());
              assertFalse(properties.isIncludeQueryParams());
              assertFalse(properties.isIncludeClientIp());
              assertTrue(properties.isLogErrorsOnly());

              assertTrue(
                  context.containsBean("httpLoggingFilter"),
                  "HttpLoggingFilter should be registered even with minimal config");
            });
  }
}
