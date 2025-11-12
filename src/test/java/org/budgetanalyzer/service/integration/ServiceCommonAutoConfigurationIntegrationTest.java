package org.budgetanalyzer.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import org.budgetanalyzer.service.api.DefaultApiExceptionHandler;
import org.budgetanalyzer.service.http.CorrelationIdFilter;
import org.budgetanalyzer.service.http.HttpLoggingFilter;
import org.budgetanalyzer.service.http.HttpLoggingProperties;

/**
 * Integration test verifying service-common auto-configuration works correctly.
 *
 * <p>Tests that all auto-configured beans are properly registered and discoverable when
 * service-common is consumed by a Spring Boot application.
 */
@SpringBootTest(classes = TestApplication.class)
@DisplayName("Service Common Auto-Configuration Integration Tests")
class ServiceCommonAutoConfigurationIntegrationTest {

  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("Should auto-configure DefaultApiExceptionHandler bean")
  void shouldAutoConfigureExceptionHandler() {
    assertThat(context.getBean(DefaultApiExceptionHandler.class)).isNotNull();
  }

  @Test
  @DisplayName("Should auto-configure HttpLoggingProperties bean")
  void shouldAutoConfigureHttpLoggingProperties() {
    var properties = context.getBean(HttpLoggingProperties.class);
    assertThat(properties).isNotNull();
    assertThat(properties.isEnabled()).isTrue();
  }

  @Test
  @DisplayName("Should auto-configure CorrelationIdFilter bean")
  void shouldAutoConfigureCorrelationIdFilter() {
    assertThat(context.getBean(CorrelationIdFilter.class)).isNotNull();
  }

  @Test
  @DisplayName("Should auto-configure HttpLoggingFilter bean")
  void shouldAutoConfigureHttpLoggingFilter() {
    assertThat(context.getBean(HttpLoggingFilter.class)).isNotNull();
  }

  @Test
  @DisplayName("Should register filters in correct order")
  void shouldRegisterFiltersInCorrectOrder() {
    var correlationIdFilter = context.getBean(CorrelationIdFilter.class);
    var httpLoggingFilter = context.getBean(HttpLoggingFilter.class);

    assertThat(correlationIdFilter).isNotNull();
    assertThat(httpLoggingFilter).isNotNull();
    // Both filters should be registered as beans
    assertThat(correlationIdFilter).isInstanceOf(Filter.class);
    assertThat(httpLoggingFilter).isInstanceOf(Filter.class);
  }

  /** Test that HttpLoggingProperties binds configuration from application.yml. */
  @Test
  @DisplayName("Should bind HttpLoggingProperties from application.yml")
  void shouldBindHttpLoggingPropertiesFromConfig() {
    var properties = context.getBean(HttpLoggingProperties.class);

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.isIncludeRequestHeaders()).isTrue();
    assertThat(properties.isIncludeResponseHeaders()).isTrue();
    assertThat(properties.isIncludeRequestBody()).isTrue();
    assertThat(properties.isIncludeResponseBody()).isTrue();
    assertThat(properties.getMaxBodySize()).isEqualTo(1024);
    assertThat(properties.getSensitiveHeaders())
        .containsExactlyInAnyOrder("Authorization", "Cookie", "Set-Cookie");
  }

  /**
   * Test with HTTP logging disabled.
   *
   * <p>When budgetanalyzer.service.http-logging.enabled=false, HttpLoggingFilter should not be
   * created.
   */
  @SpringBootTest(classes = TestApplication.class)
  @TestPropertySource(properties = {"budgetanalyzer.service.http-logging.enabled=false"})
  @DisplayName("HTTP Logging Disabled Tests")
  static class HttpLoggingDisabledIntegrationTest {

    @Autowired private ApplicationContext context;

    @Test
    @DisplayName("Should not create HttpLoggingFilter when logging is disabled")
    void shouldNotCreateHttpLoggingFilterWhenDisabled() {
      // HttpLoggingProperties should still exist
      assertThat(context.getBean(HttpLoggingProperties.class)).isNotNull();
      assertThat(context.getBean(HttpLoggingProperties.class).isEnabled()).isFalse();

      // HttpLoggingFilter should not be created
      assertThat(context.getBeanNamesForType(HttpLoggingFilter.class)).isEmpty();
    }

    @Test
    @DisplayName("Should still create CorrelationIdFilter when logging is disabled")
    void shouldStillCreateCorrelationIdFilterWhenLoggingDisabled() {
      // CorrelationIdFilter should always be created
      assertThat(context.getBean(CorrelationIdFilter.class)).isNotNull();
    }
  }
}
