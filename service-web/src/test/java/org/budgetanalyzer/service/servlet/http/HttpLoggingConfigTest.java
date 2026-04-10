package org.budgetanalyzer.service.servlet.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import org.budgetanalyzer.service.config.HttpLoggingProperties;
import org.budgetanalyzer.service.config.ServiceWebAutoConfiguration;

class HttpLoggingConfigTest {

  private final WebApplicationContextRunner webContextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ServiceWebAutoConfiguration.class));

  private final ApplicationContextRunner nonWebContextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ServiceWebAutoConfiguration.class));

  @Test
  void shouldRegisterCorrelationIdFilterInWebApplication() {
    // Arrange & Act
    webContextRunner.run(
        context -> {
          // Assert
          assertThat(context.containsBean("correlationIdFilter"))
              .as("Should register CorrelationIdFilter bean")
              .isTrue();
          assertThat(context.getBean(CorrelationIdFilter.class))
              .as("Should be able to get CorrelationIdFilter bean")
              .isNotNull();
        });
  }

  @Test
  void shouldNotRegisterCorrelationIdFilterInNonWebApplication() {
    // Arrange & Act
    nonWebContextRunner.run(
        context -> {
          // Assert
          assertThat(context.containsBean("correlationIdFilter"))
              .as("Should NOT register CorrelationIdFilter in non-web application")
              .isFalse();
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
              assertThat(context.containsBean("httpLoggingFilter"))
                  .as("Should register HttpLoggingFilter when enabled")
                  .isTrue();
              assertThat(context.getBean(HttpLoggingFilter.class))
                  .as("Should be able to get HttpLoggingFilter bean")
                  .isNotNull();
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
              assertThat(context.containsBean("httpLoggingFilter"))
                  .as("Should NOT register HttpLoggingFilter when disabled")
                  .isFalse();
            });
  }

  @Test
  void shouldNotRegisterHttpLoggingFilterWhenPropertyNotSet() {
    // Arrange & Act - Don't set the enabled property (defaults to false)
    webContextRunner.run(
        context -> {
          // Assert
          assertThat(context.containsBean("httpLoggingFilter"))
              .as("Should NOT register HttpLoggingFilter when property not set (defaults to false)")
              .isFalse();
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
              assertThat(context.containsBean("correlationIdFilter"))
                  .as("Should register CorrelationIdFilter")
                  .isTrue();
              assertThat(context.containsBean("httpLoggingFilter"))
                  .as("Should register HttpLoggingFilter")
                  .isTrue();

              assertThat(
                      context.getBeansOfType(CorrelationIdFilter.class).size()
                          + context.getBeansOfType(HttpLoggingFilter.class).size())
                  .as("Should have exactly 2 filter beans registered")
                  .isEqualTo(2);
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
              assertThat(context.containsBean("correlationIdFilter"))
                  .as("Should register CorrelationIdFilter")
                  .isTrue();
              assertThat(context.containsBean("httpLoggingFilter"))
                  .as("Should NOT register HttpLoggingFilter")
                  .isFalse();

              assertThat(context.getBeansOfType(CorrelationIdFilter.class).size())
                  .as("Should have exactly 1 filter bean (CorrelationIdFilter)")
                  .isEqualTo(1);
              assertThat(context.getBeansOfType(HttpLoggingFilter.class).size())
                  .as("Should have 0 HttpLoggingFilter beans")
                  .isEqualTo(0);
            });
  }

  @Test
  void shouldRegisterHttpLoggingPropertiesBean() {
    // Arrange & Act
    webContextRunner.run(
        context -> {
          // Assert
          assertThat(context.getBean(HttpLoggingProperties.class))
              .as("Should be able to get HttpLoggingProperties bean")
              .isNotNull();
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
              assertThat(properties.isEnabled()).as("Properties should have enabled=true").isTrue();
              assertThat(properties.getLogLevel())
                  .as("Properties should have log-level=INFO")
                  .isEqualTo("INFO");
              assertThat(properties.getMaxBodySize())
                  .as("Properties should have max-body-size=5000")
                  .isEqualTo(5000);
              assertThat(properties.isIncludeRequestBody())
                  .as("Properties should have include-request-body=false")
                  .isFalse();

              // Filter should be created with these properties
              assertThat(context.getBean(HttpLoggingFilter.class))
                  .as("HttpLoggingFilter should be created with properties")
                  .isNotNull();
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
              assertThat(properties.getExcludePatterns().size())
                  .as("Should have 2 exclude patterns")
                  .isEqualTo(2);
              assertThat(properties.getIncludePatterns().size())
                  .as("Should have 1 include pattern")
                  .isEqualTo(1);
              assertThat(properties.getExcludePatterns().contains("/actuator/**"))
                  .as("Should contain /actuator/** exclude pattern")
                  .isTrue();
              assertThat(properties.getExcludePatterns().contains("/swagger-ui/**"))
                  .as("Should contain /swagger-ui/** exclude pattern")
                  .isTrue();
              assertThat(properties.getIncludePatterns().contains("/api/**"))
                  .as("Should contain /api/** include pattern")
                  .isTrue();
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
              assertThat(properties.getSensitiveHeaders().size())
                  .as("Should have 2 sensitive headers")
                  .isEqualTo(2);
              assertThat(properties.getSensitiveHeaders().contains("X-Custom-Token"))
                  .as("Should contain X-Custom-Token")
                  .isTrue();
              assertThat(properties.getSensitiveHeaders().contains("X-API-Secret"))
                  .as("Should contain X-API-Secret")
                  .isTrue();
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
              assertThat(properties.isLogErrorsOnly())
                  .as("Should have log-errors-only=true")
                  .isTrue();
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
              assertThat(context.containsBean("correlationIdFilter"))
                  .as("Should NOT register CorrelationIdFilter in non-web application")
                  .isFalse();
              assertThat(context.containsBean("httpLoggingFilter"))
                  .as("Should NOT register HttpLoggingFilter in non-web application")
                  .isFalse();

              // HttpLoggingConfig is conditional on web application,
              // so properties won't be registered either
              assertThat(context.getBeansOfType(HttpLoggingProperties.class).size())
                  .as("Should NOT register HttpLoggingProperties in non-web application")
                  .isEqualTo(0);
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
              assertThat(properties.isEnabled()).isTrue();
              assertThat(properties.isIncludeRequestBody()).isTrue();
              assertThat(properties.isIncludeResponseBody()).isTrue();
              assertThat(properties.isIncludeRequestHeaders()).isTrue();
              assertThat(properties.isIncludeResponseHeaders()).isTrue();
              assertThat(properties.isIncludeQueryParams()).isTrue();
              assertThat(properties.isIncludeClientIp()).isTrue();
              assertThat(properties.isLogErrorsOnly()).isFalse();
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
              assertThat(properties.isEnabled()).as("Filter should be enabled").isTrue();
              assertThat(properties.isIncludeRequestBody()).isFalse();
              assertThat(properties.isIncludeResponseBody()).isFalse();
              assertThat(properties.isIncludeRequestHeaders()).isFalse();
              assertThat(properties.isIncludeResponseHeaders()).isFalse();
              assertThat(properties.isIncludeQueryParams()).isFalse();
              assertThat(properties.isIncludeClientIp()).isFalse();
              assertThat(properties.isLogErrorsOnly()).isTrue();

              assertThat(context.containsBean("httpLoggingFilter"))
                  .as("HttpLoggingFilter should be registered even with minimal config")
                  .isTrue();
            });
  }
}
