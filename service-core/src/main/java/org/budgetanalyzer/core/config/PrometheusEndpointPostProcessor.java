package org.budgetanalyzer.core.config;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Ensures the Prometheus actuator endpoint is always exposed.
 *
 * <p>This post-processor adds "prometheus" to the list of exposed web endpoints, merging with any
 * existing configuration. Services cannot opt out of prometheus exposure via endpoint config.
 *
 * <p>Downstream services automatically get Prometheus metrics at /actuator/prometheus without any
 * configuration changes.
 */
public class PrometheusEndpointPostProcessor implements EnvironmentPostProcessor, Ordered {

  private static final String EXPOSURE_PROPERTY = "management.endpoints.web.exposure.include";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (!isPrometheusOnClasspath()) {
      return;
    }

    String existing = environment.getProperty(EXPOSURE_PROPERTY);
    Set<String> endpoints = parseEndpoints(existing);
    endpoints.add("prometheus");

    MapPropertySource propertySource =
        new MapPropertySource(
            "prometheusDefaults", java.util.Map.of(EXPOSURE_PROPERTY, String.join(",", endpoints)));

    environment.getPropertySources().addFirst(propertySource);
  }

  private boolean isPrometheusOnClasspath() {
    try {
      Class.forName("io.micrometer.prometheusmetrics.PrometheusMeterRegistry");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private Set<String> parseEndpoints(String value) {
    Set<String> endpoints = new LinkedHashSet<>();
    if (value != null && !value.isBlank()) {
      for (String endpoint : value.split(",")) {
        String trimmed = endpoint.trim();
        if (!trimmed.isEmpty()) {
          endpoints.add(trimmed);
        }
      }
    }
    return endpoints;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
