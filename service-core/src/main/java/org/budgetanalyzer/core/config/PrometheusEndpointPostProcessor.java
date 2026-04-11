package org.budgetanalyzer.core.config;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Provides default Prometheus actuator endpoint exposure and metrics export.
 *
 * <p>This post-processor adds a low-priority property source that publishes two fixed defaults:
 *
 * <ol>
 *   <li>{@code management.endpoints.web.exposure.include=health,prometheus}
 *   <li>{@code management.prometheus.metrics.export.enabled=true}
 * </ol>
 *
 * <p>The source is added via {@code addLast}, so any explicit value set by a consumer service wins
 * unchanged. The post-processor does <strong>not</strong> merge its defaults into a consumer-set
 * exposure list: any consumer that sets {@code management.endpoints.web.exposure.include} itself is
 * responsible for keeping {@code prometheus} in the list if it wants the scrape endpoint exposed.
 *
 * <p>Downstream services that set neither property automatically get Prometheus metrics at {@code
 * /actuator/prometheus} with no configuration changes.
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

    var propertySource =
        new MapPropertySource(
            "prometheusDefaults",
            Map.of(EXPOSURE_PROPERTY, "health,prometheus", PROMETHEUS_EXPORT_ENABLED, "true"));

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

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
