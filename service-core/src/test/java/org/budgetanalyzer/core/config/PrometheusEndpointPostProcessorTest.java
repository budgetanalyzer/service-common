package org.budgetanalyzer.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    assertEquals("prometheus", result);
  }

  @Test
  void shouldMergePrometheusWithExistingConfig() {
    // Arrange - simulate app-defined config
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource("application", Map.of(EXPOSURE_PROPERTY, "health,info,metrics")));

    // Act
    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    // Assert - prometheus should be merged AND should win (addFirst)
    var result = environment.getProperty(EXPOSURE_PROPERTY);
    Set<String> endpoints = new HashSet<>(Arrays.asList(result.split(",")));
    assertTrue(endpoints.contains("prometheus"), "Should include prometheus");
    assertTrue(endpoints.contains("health"), "Should preserve health");
    assertTrue(endpoints.contains("info"), "Should preserve info");
    assertTrue(endpoints.contains("metrics"), "Should preserve metrics");
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

    // Assert
    var result = environment.getProperty(EXPOSURE_PROPERTY);
    long prometheusCount =
        Arrays.stream(result.split(",")).filter(e -> e.trim().equals("prometheus")).count();
    assertEquals(1, prometheusCount, "Should not duplicate prometheus");
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

    // Assert
    var result = environment.getProperty(EXPOSURE_PROPERTY);
    Set<String> endpoints = new HashSet<>(Arrays.asList(result.split(",")));
    assertTrue(endpoints.contains("prometheus"), "Should include prometheus");
  }
}
