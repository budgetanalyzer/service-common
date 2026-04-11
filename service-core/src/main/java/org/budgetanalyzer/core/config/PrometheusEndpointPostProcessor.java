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
 * Provides default Prometheus actuator endpoint exposure and metrics export.
 *
 * <p>This post-processor adds a low-priority property source that:
 *
 * <ol>
 *   <li>Includes "prometheus" in {@code management.endpoints.web.exposure.include} (merged with
 *       "health" as the baseline when no existing value is set).
 *   <li>Enables Prometheus metrics export via {@code
 *       management.prometheus.metrics.export.enabled=true}.
 * </ol>
 *
 * <p>Because the source is added via {@code addLast}, any explicit properties set by a consumer
 * service take precedence.
 *
 * <p>Downstream services automatically get Prometheus metrics at /actuator/prometheus without any
 * configuration changes, but can override both the exposure list and the export flag when needed.
 */
public class PrometheusEndpointPostProcessor implements EnvironmentPostProcessor, Ordered {

  private static final String EXPOSURE_PROPERTY = "management.endpoints.web.exposure.include";
  private static final String PROMETHEUS_EXPORT_ENABLED =
      "management.prometheus.metrics.export.enabled";

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
            "prometheusDefaults",
            Map.of(
                EXPOSURE_PROPERTY, String.join(",", endpoints), PROMETHEUS_EXPORT_ENABLED, "true"));

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
