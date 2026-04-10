package org.budgetanalyzer.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PrometheusEndpointPostProcessorTest {

  private static final String EXPOSURE_PROPERTY = "management.endpoints.web.exposure.include";

  private PrometheusEndpointPostProcessor postProcessor;
  private MockEnvironment environment;

  @BeforeEach
  void setUp() {
    postProcessor = new PrometheusEndpointPostProcessor();
    environment = new MockEnvironment();
  }

  @Test
  void shouldAddPrometheusWhenNoExistingConfig() {
    // Act
    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    // Assert
    var result = environment.getProperty(EXPOSURE_PROPERTY);
    assertThat(result).isEqualTo("health,prometheus");
  }

  @Test
  void shouldNotOverrideExistingConfig() {
    // Arrange
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource("application", Map.of(EXPOSURE_PROPERTY, "health,info,metrics")));

    // Act
    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    // Assert -- consumer's property source wins because prometheusDefaults is addLast
    var result = environment.getProperty(EXPOSURE_PROPERTY);
    assertThat(result).isEqualTo("health,info,metrics");
  }

  @Test
  void shouldNotDuplicatePrometheusIfAlreadyPresent() {
    // Arrange
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "application", Map.of(EXPOSURE_PROPERTY, "health,prometheus,metrics")));

    // Act
    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    // Assert -- consumer source wins; prometheus appears once in that value
    var result = environment.getProperty(EXPOSURE_PROPERTY);
    assertThat(result).isEqualTo("health,prometheus,metrics");
  }

  @Test
  void shouldHandleWhitespaceInExistingConfig() {
    // Arrange
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "application", Map.of(EXPOSURE_PROPERTY, " health , info , metrics ")));

    // Act
    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    // Assert -- consumer source wins
    var result = environment.getProperty(EXPOSURE_PROPERTY);
    assertThat(result).isEqualTo(" health , info , metrics ");
  }
}
