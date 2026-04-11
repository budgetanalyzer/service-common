package org.budgetanalyzer.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration test verifying the Prometheus actuator endpoint is exposed by default when a reactive
 * (WebFlux) application consumes service-common.
 *
 * <p>This boots a real Spring Boot reactive application context and asserts that {@code
 * /actuator/prometheus} is reachable and returns JVM metrics. The test proves that the {@link
 * org.budgetanalyzer.core.config.PrometheusEndpointPostProcessor} works correctly in reactive
 * service deployments.
 */
@SpringBootTest(
    classes = ReactiveTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.main.web-application-type=reactive"})
@AutoConfigureWebTestClient
@DisplayName("Prometheus Endpoint Reactive Integration Tests")
class PrometheusEndpointReactiveIntegrationTest {

  @Autowired private WebTestClient webTestClient;
  @Autowired private Environment environment;

  @Test
  @DisplayName("should expose /actuator/prometheus returning HTTP 200")
  void shouldExposePrometheusEndpoint() {
    webTestClient.get().uri("/actuator/prometheus").exchange().expectStatus().isOk();
  }

  @Test
  @DisplayName("should return JVM metrics from /actuator/prometheus")
  void shouldReturnJvmMetrics() {
    webTestClient
        .get()
        .uri("/actuator/prometheus")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("jvm_info"));
  }

  @Test
  @DisplayName("should include prometheus in management endpoint exposure")
  void shouldIncludePrometheusInEndpointExposure() {
    var exposure = environment.getProperty("management.endpoints.web.exposure.include");
    assertThat(exposure).isNotNull();
    assertThat(exposure).contains("prometheus");
  }
}
