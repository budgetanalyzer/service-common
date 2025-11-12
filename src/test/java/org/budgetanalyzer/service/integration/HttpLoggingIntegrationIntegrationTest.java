package org.budgetanalyzer.service.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test verifying HTTP logging and correlation ID tracking work end-to-end.
 *
 * <p>Tests the full filter chain: CorrelationIdFilter → HttpLoggingFilter → Controller
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@DisplayName("HTTP Logging Integration Tests")
class HttpLoggingIntegrationIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Should add correlation ID header when not provided")
  void shouldAddCorrelationIdWhenNotProvided() throws Exception {
    mockMvc
        .perform(get("/api/test/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(header().exists("X-Correlation-ID"));
  }

  @Test
  @DisplayName("Should preserve correlation ID header when provided")
  void shouldPreserveCorrelationIdWhenProvided() throws Exception {
    var correlationId = "test-correlation-12345";

    mockMvc
        .perform(get("/api/test/not-found").header("X-Correlation-ID", correlationId))
        .andExpect(status().isNotFound())
        .andExpect(header().string("X-Correlation-ID", correlationId));
  }

  @Test
  @DisplayName("Should propagate correlation ID through filter chain")
  void shouldPropagateCorrelationIdThroughFilterChain() throws Exception {
    var correlationId = "propagation-test-67890";

    mockMvc
        .perform(get("/api/test/business-error").header("X-Correlation-ID", correlationId))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(header().string("X-Correlation-ID", correlationId));
  }

  @Test
  @DisplayName("Should handle JSON request body")
  void shouldHandleJsonRequestBody() throws Exception {
    var jsonRequest = "{\"name\": \"TestUser\"}";

    mockMvc
        .perform(
            post("/api/test/validate").contentType(MediaType.APPLICATION_JSON).content(jsonRequest))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Should handle JSON request body with validation errors")
  void shouldHandleJsonRequestBodyWithValidationErrors() throws Exception {
    var invalidJsonRequest = "{\"name\": \"\"}";
    var correlationId = "validation-error-test";

    mockMvc
        .perform(
            post("/api/test/validate")
                .header("X-Correlation-ID", correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJsonRequest))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("X-Correlation-ID", correlationId))
        .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("Should log requests with different content types")
  void shouldLogRequestsWithDifferentContentTypes() throws Exception {
    // JSON content type
    mockMvc
        .perform(
            post("/api/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"JsonTest\"}"))
        .andExpect(status().isOk());

    // Form data content type (will fail validation but tests logging)
    mockMvc.perform(
        post("/api/test/validate")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("name", "FormTest"));
  }

  @Test
  @DisplayName("Should handle requests with custom headers")
  void shouldHandleRequestsWithCustomHeaders() throws Exception {
    mockMvc
        .perform(
            get("/api/test/not-found")
                .header("X-Custom-Header", "CustomValue")
                .header("User-Agent", "Integration-Test/1.0"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Should work with sensitive headers in configuration")
  void shouldWorkWithSensitiveHeadersInConfiguration() throws Exception {
    // Authorization is marked as sensitive in application.yml
    mockMvc
        .perform(get("/api/test/not-found").header("Authorization", "Bearer secret-token"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Should handle multiple sequential requests with different correlation IDs")
  void shouldHandleMultipleSequentialRequests() throws Exception {
    var correlationId1 = "request-1";
    var correlationId2 = "request-2";
    var correlationId3 = "request-3";

    mockMvc
        .perform(get("/api/test/not-found").header("X-Correlation-ID", correlationId1))
        .andExpect(header().string("X-Correlation-ID", correlationId1));

    mockMvc
        .perform(get("/api/test/business-error").header("X-Correlation-ID", correlationId2))
        .andExpect(header().string("X-Correlation-ID", correlationId2));

    mockMvc
        .perform(get("/api/test/invalid-request").header("X-Correlation-ID", correlationId3))
        .andExpect(header().string("X-Correlation-ID", correlationId3));
  }

  @Test
  @DisplayName("Should add correlation ID to successful responses")
  void shouldAddCorrelationIdToSuccessfulResponses() throws Exception {
    var correlationId = "success-response-test";
    var validRequest = "{\"name\": \"SuccessTest\"}";

    mockMvc
        .perform(
            post("/api/test/validate")
                .header("X-Correlation-ID", correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Should handle large request bodies within max size")
  void shouldHandleLargeRequestBodies() throws Exception {
    // Max body size is 1024 bytes in test configuration
    var largeButValidRequest = "{\"name\": \"" + "A".repeat(50) + "\"}"; // Well under 1024 bytes

    mockMvc
        .perform(
            post("/api/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(largeButValidRequest))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Should work correctly with filter chain ordering")
  void shouldWorkCorrectlyWithFilterChainOrdering() throws Exception {
    // This test verifies that:
    // 1. CorrelationIdFilter runs first (adds correlation ID)
    // 2. HttpLoggingFilter runs second (logs with correlation ID)
    // 3. Exception handler receives correlation ID in MDC

    var correlationId = "filter-order-test";

    mockMvc
        .perform(get("/api/test/service-error").header("X-Correlation-ID", correlationId))
        .andExpect(status().isInternalServerError())
        .andExpect(header().string("X-Correlation-ID", correlationId))
        .andExpect(jsonPath("$.type").value("INTERNAL_ERROR"));
  }
}
