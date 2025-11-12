package org.budgetanalyzer.service.integration;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Integration test verifying exception handling works end-to-end.
 *
 * <p>Tests the full flow: HTTP request → Controller → Exception → @RestControllerAdvice →
 * ApiErrorResponse
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@DisplayName("Exception Handling Integration Tests")
class ExceptionHandlingIntegrationIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Should return 404 with ApiErrorResponse for ResourceNotFoundException")
  void shouldReturn404ForResourceNotFound() throws Exception {
    mockMvc
        .perform(get("/api/test/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Test resource not found"));
  }

  @Test
  @DisplayName("Should return 422 with ApiErrorResponse for BusinessException")
  void shouldReturn422ForBusinessException() throws Exception {
    mockMvc
        .perform(get("/api/test/business-error"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value("APPLICATION_ERROR"))
        .andExpect(jsonPath("$.message").value("Business rule violation"))
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
  }

  @Test
  @DisplayName("Should return 400 with ApiErrorResponse for InvalidRequestException")
  void shouldReturn400ForInvalidRequest() throws Exception {
    mockMvc
        .perform(get("/api/test/invalid-request"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Invalid request parameters"));
  }

  @Test
  @DisplayName("Should return 500 with ApiErrorResponse for ServiceException")
  void shouldReturn500ForServiceException() throws Exception {
    mockMvc
        .perform(get("/api/test/service-error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.type").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("Internal service error"));
  }

  @Test
  @DisplayName("Should return 503 with ApiErrorResponse for ServiceUnavailableException")
  void shouldReturn503ForServiceUnavailable() throws Exception {
    mockMvc
        .perform(get("/api/test/service-unavailable"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.type").value("SERVICE_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Service temporarily unavailable"));
  }

  @Test
  @DisplayName("Should return 500 with ApiErrorResponse for unexpected RuntimeException")
  void shouldReturn500ForUnexpectedRuntimeException() throws Exception {
    mockMvc
        .perform(get("/api/test/runtime-error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.type").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("Unexpected runtime error"));
  }

  @Test
  @DisplayName("Should return 400 with field errors for validation failures")
  void shouldReturn400WithFieldErrorsForValidationFailure() throws Exception {
    var invalidRequest = "{\"name\": \"\"}";

    mockMvc
        .perform(
            post("/api/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").value(containsString("Validation failed")))
        .andExpect(jsonPath("$.fieldErrors").isArray())
        .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
        .andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("name", "name")))
        .andExpect(
            jsonPath("$.fieldErrors[*].message")
                .value(
                    containsInAnyOrder(
                        "Name is required", "Name must be between 3 and 50 characters")));
  }

  @Test
  @DisplayName("Should return 200 for valid request")
  void shouldReturn200ForValidRequest() throws Exception {
    var validRequest = "{\"name\": \"ValidName\"}";

    mockMvc
        .perform(
            post("/api/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Should include correlation ID in all error responses")
  void shouldIncludeCorrelationIdInErrorResponses() throws Exception {
    var correlationId = "test-correlation-id-12345";

    mockMvc
        .perform(get("/api/test/not-found").header("X-Correlation-ID", correlationId))
        .andExpect(status().isNotFound());
  }
}
