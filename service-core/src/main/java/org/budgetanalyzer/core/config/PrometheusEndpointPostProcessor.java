package org.budgetanalyzer.core.config;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Provides default Prometheus actuator endpoint exposure.
 *
 * <p>This post-processor adds a low-priority property source containing "health,prometheus" (or
 * merges "prometheus" into an existing list). Because the source is added via {@code addLast}, any
 * explicit {@code management.endpoints.web.exposure.include} set by a consumer service takes
 * precedence.
 *
 * <p>Downstream services automatically get Prometheus metrics at /actuator/prometheus without any
 * configuration changes, but can override the full exposure list when needed.
 */
public class PrometheusEndpointPostProcessor implements EnvironmentPostProcessor, Ordered {

  private static final String EXPOSURE_PROPERTY = "management.endpoints.web.exposure.include";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (!isPrometheusOnClasspath()) {
      return;
    }

    var existing = environment.getProperty(EXPOSURE_PROPERTY);
    var endpoints = parseEndpoints(existing);
    if (endpoints.isEmpty()) {
      endpoints.add("health");
    }
    endpoints.add("prometheus");

    var propertySource =
        new MapPropertySource(
            "prometheusDefaults", Map.of(EXPOSURE_PROPERTY, String.join(",", endpoints)));

    environment.getPropertySources().addLast(propertySource);
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
    var endpoints = new LinkedHashSet<String>();
    if (value != null && !value.isBlank()) {
      for (var endpoint : value.split(",")) {
        var trimmed = endpoint.trim();
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
