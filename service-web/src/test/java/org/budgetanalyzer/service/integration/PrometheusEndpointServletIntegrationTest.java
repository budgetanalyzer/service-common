package org.budgetanalyzer.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import org.budgetanalyzer.service.security.test.TestClaimsSecurityConfig;

/**
 * Integration test verifying the Prometheus actuator endpoint is exposed by default when a servlet
 * application consumes service-common.
 *
 * <p>This boots a real Spring Boot application context with service-web auto-configuration and
 * asserts that {@code /actuator/prometheus} is reachable and returns JVM metrics. The test proves
 * that the {@link org.budgetanalyzer.core.config.PrometheusEndpointPostProcessor} is loaded from
 * the service-core dependency and exposes the endpoint without any explicit consumer configuration.
 */
@SpringBootTest(
    classes = {ServletTestApplication.class, TestClaimsSecurityConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.main.web-application-type=servlet"})
@AutoConfigureMockMvc
@DisplayName("Prometheus Endpoint Servlet Integration Tests")
class PrometheusEndpointServletIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private Environment environment;

  @Test
  @DisplayName("should expose /actuator/prometheus returning HTTP 200")
  void shouldExposePrometheusEndpoint() throws Exception {
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("should return JVM metrics from /actuator/prometheus")
  void shouldReturnJvmMetrics() throws Exception {
    var result =
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk()).andReturn();

    var body = result.getResponse().getContentAsString();
    assertThat(body).contains("jvm_info");
  }

  @Test
  @DisplayName("should include prometheus in management endpoint exposure")
  void shouldIncludePrometheusInEndpointExposure() {
    var exposure = environment.getProperty("management.endpoints.web.exposure.include");
    assertThat(exposure).isNotNull();
    assertThat(exposure).contains("prometheus");
  }
}
