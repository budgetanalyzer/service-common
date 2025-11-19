package org.budgetanalyzer.service.reactive.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

import org.budgetanalyzer.service.config.HttpLoggingProperties;
import org.budgetanalyzer.service.config.ServiceWebAutoConfiguration;

class ReactiveHttpLoggingConfigTest {

  private final ReactiveWebApplicationContextRunner reactiveContextRunner =
      new ReactiveWebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ServiceWebAutoConfiguration.class));

  private final ApplicationContextRunner nonWebContextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ServiceWebAutoConfiguration.class));

  @Test
  void shouldRegisterReactiveCorrelationIdFilterInReactiveWebApplication() {
    // Arrange & Act
    reactiveContextRunner.run(
        context -> {
          // Assert
          assertTrue(
              context.containsBean("reactiveCorrelationIdFilter"),
              "Should register ReactiveCorrelationIdFilter bean");
          assertNotNull(
              context.getBean(ReactiveCorrelationIdFilter.class),
              "Should be able to get ReactiveCorrelationIdFilter bean");
        });
  }

  @Test
  void shouldNotRegisterReactiveFiltersInNonWebApplication() {
    // Arrange & Act
    nonWebContextRunner.run(
        context -> {
          // Assert
          assertFalse(
              context.containsBean("reactiveCorrelationIdFilter"),
              "Should NOT register ReactiveCorrelationIdFilter in non-web application");
          assertFalse(
              context.containsBean("reactiveHttpLoggingFilter"),
              "Should NOT register ReactiveHttpLoggingFilter in non-web application");
        });
  }

  @Test
  void shouldRegisterReactiveHttpLoggingFilterWhenEnabled() {
    // Arrange & Act
    reactiveContextRunner
        .withPropertyValues("budgetanalyzer.service.http-logging.enabled=true")
        .run(
            context -> {
              // Assert
              assertTrue(
                  context.containsBean("reactiveHttpLoggingFilter"),
                  "Should register ReactiveHttpLoggingFilter when enabled");
              assertNotNull(
                  context.getBean(ReactiveHttpLoggingFilter.class),
                  "Should be able to get ReactiveHttpLoggingFilter bean");
            });
  }

  @Test
  void shouldNotRegisterReactiveHttpLoggingFilterWhenDisabled() {
    // Arrange & Act
    reactiveContextRunner
        .withPropertyValues("budgetanalyzer.service.http-logging.enabled=false")
        .run(
            context -> {
              // Assert
              assertFalse(
                  context.containsBean("reactiveHttpLoggingFilter"),
                  "Should NOT register ReactiveHttpLoggingFilter when disabled");
            });
  }

  @Test
  void shouldNotRegisterReactiveHttpLoggingFilterWhenPropertyNotSet() {
    // Arrange & Act - Don't set the enabled property (defaults to false)
    reactiveContextRunner.run(
        context -> {
          // Assert
          assertFalse(
              context.containsBean("reactiveHttpLoggingFilter"),
              "Should NOT register ReactiveHttpLoggingFilter when property not set "
                  + "(default false)");
        });
  }

  @Test
  void shouldRegisterBothFiltersWhenHttpLoggingEnabled() {
    // Arrange & Act
    reactiveContextRunner
        .withPropertyValues("budgetanalyzer.service.http-logging.enabled=true")
        .run(
            context -> {
              // Assert
              assertTrue(
                  context.containsBean("reactiveCorrelationIdFilter"),
                  "Should register ReactiveCorrelationIdFilter");
              assertTrue(
                  context.containsBean("reactiveHttpLoggingFilter"),
                  "Should register ReactiveHttpLoggingFilter");

              assertEquals(
                  2,
                  context.getBeansOfType(ReactiveCorrelationIdFilter.class).size()
                      + context.getBeansOfType(ReactiveHttpLoggingFilter.class).size(),
                  "Should have exactly 2 filter beans registered");
            });
  }

  @Test
  void shouldRegisterOnlyReactiveCorrelationIdFilterWhenHttpLoggingDisabled() {
    // Arrange & Act
    reactiveContextRunner
        .withPropertyValues("budgetanalyzer.service.http-logging.enabled=false")
        .run(
            context -> {
              // Assert
              assertTrue(
                  context.containsBean("reactiveCorrelationIdFilter"),
                  "Should register ReactiveCorrelationIdFilter");
              assertFalse(
                  context.containsBean("reactiveHttpLoggingFilter"),
                  "Should NOT register ReactiveHttpLoggingFilter");

              assertEquals(
                  1,
                  context.getBeansOfType(ReactiveCorrelationIdFilter.class).size(),
                  "Should have exactly 1 filter bean (ReactiveCorrelationIdFilter)");
              assertEquals(
                  0,
                  context.getBeansOfType(ReactiveHttpLoggingFilter.class).size(),
                  "Should have 0 ReactiveHttpLoggingFilter beans");
            });
  }

  @Test
  void shouldRegisterHttpLoggingPropertiesBean() {
    // Arrange & Act
    reactiveContextRunner.run(
        context -> {
          // Assert
          assertNotNull(
              context.getBean(HttpLoggingProperties.class),
              "Should be able to get HttpLoggingProperties bean");
        });
  }

  @Test
  void shouldPassPropertiesToReactiveHttpLoggingFilter() {
    // Arrange & Act
    reactiveContextRunner
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
                  context.getBean(ReactiveHttpLoggingFilter.class),
                  "ReactiveHttpLoggingFilter should be created with properties");
            });
  }

  @Test
  void shouldHandleExcludeAndIncludePatterns() {
    // Arrange & Act
    reactiveContextRunner
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
    reactiveContextRunner
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
    reactiveContextRunner
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
                  context.containsBean("reactiveCorrelationIdFilter"),
                  "Should NOT register ReactiveCorrelationIdFilter in non-web application");
              assertFalse(
                  context.containsBean("reactiveHttpLoggingFilter"),
                  "Should NOT register ReactiveHttpLoggingFilter in non-web application");

              // ReactiveHttpLoggingConfig is conditional on web application,
              // so properties won't be registered by reactive config
              // (but might be registered by servlet config if present)
            });
  }

  @Test
  void shouldHandleAllBooleanPropertiesCombinations() {
    // Arrange & Act - Enable all optional features
    reactiveContextRunner
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
    reactiveContextRunner
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
                  context.containsBean("reactiveHttpLoggingFilter"),
                  "ReactiveHttpLoggingFilter should be registered even with minimal config");
            });
  }
}
