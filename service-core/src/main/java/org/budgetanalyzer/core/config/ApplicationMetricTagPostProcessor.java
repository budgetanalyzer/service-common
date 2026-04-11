package org.budgetanalyzer.core.config;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Adds an {@code application} common tag to every Micrometer meter so metrics carry the service
 * identity regardless of which registry is in use or how the scrape pipeline is configured.
 *
 * <p>This post-processor adds a low-priority property source that sets {@code
 * management.metrics.tags.application=${spring.application.name}}. The placeholder is resolved by
 * Spring when the property is read during context initialization, not at post-process time, so the
 * consumer service only needs to set {@code spring.application.name} (which it already does).
 *
 * <p>Because the source is added via {@code addLast}, any explicit value set by a consumer service
 * takes precedence.
 *
 * <p>Unlike {@link PrometheusEndpointPostProcessor}, this processor is registry-agnostic: common
 * tags apply to any Micrometer registry, so no classpath guard is needed.
 */
public class ApplicationMetricTagPostProcessor implements EnvironmentPostProcessor, Ordered {

  private static final String APPLICATION_TAG_PROPERTY = "management.metrics.tags.application";
  private static final String APPLICATION_NAME_PLACEHOLDER = "${spring.application.name}";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    var propertySource =
        new MapPropertySource(
            "applicationMetricTagDefaults",
            Map.of(APPLICATION_TAG_PROPERTY, APPLICATION_NAME_PLACEHOLDER));
    environment.getPropertySources().addLast(propertySource);
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
