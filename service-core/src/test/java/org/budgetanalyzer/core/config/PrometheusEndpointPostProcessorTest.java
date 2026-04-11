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
  private static final String PROMETHEUS_EXPORT_ENABLED =
      "management.prometheus.metrics.export.enabled";

  private PrometheusEndpointPostProcessor postProcessor;
  private MockEnvironment environment;

  @BeforeEach
  void setUp() {
    postProcessor = new PrometheusEndpointPostProcessor();
    environment = new MockEnvironment();
  }

  @Test
  void shouldPublishStaticDefaultExposureList() {
    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    var result = environment.getProperty(EXPOSURE_PROPERTY);
    assertThat(result).isEqualTo("health,prometheus");
  }

  @Test
  void shouldEnablePrometheusMetricsExport() {
    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    var result = environment.getProperty(PROMETHEUS_EXPORT_ENABLED);
    assertThat(result).isEqualTo("true");
  }

  @Test
  void shouldNotOverrideExistingConfig() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource("application", Map.of(EXPOSURE_PROPERTY, "health,info,metrics")));

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    // Consumer value wins unchanged: the post-processor does not merge its default into the
    // consumer's exposure list, so callers that set this property themselves are responsible for
    // keeping "prometheus" in the value if they want the scrape endpoint exposed.
    var result = environment.getProperty(EXPOSURE_PROPERTY);
    assertThat(result).isEqualTo("health,info,metrics");
  }

  @Test
  void shouldNotMergePrometheusIntoConsumerExposureList() {
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("application", Map.of(EXPOSURE_PROPERTY, "health,info")));

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    // Option A contract: the post-processor never merges. A consumer that drops "prometheus" from
    // its exposure list loses the scrape endpoint — it is the consumer's responsibility to keep it.
    var result = environment.getProperty(EXPOSURE_PROPERTY);
    assertThat(result).isEqualTo("health,info");
    assertThat(result).doesNotContain("prometheus");
  }
}
