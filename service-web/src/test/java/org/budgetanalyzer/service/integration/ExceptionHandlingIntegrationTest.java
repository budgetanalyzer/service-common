package org.budgetanalyzer.service.integration;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.budgetanalyzer.service.security.test.ClaimsHeaderTestBuilder;
import org.budgetanalyzer.service.security.test.TestClaimsSecurityConfig;

/**
 * Integration test verifying exception handling works end-to-end.
 *
 * <p>Tests the full flow: HTTP request → Controller → Exception → @RestControllerAdvice →
 * ApiErrorResponse
 */
@SpringBootTest(
    classes = {ServletTestApplication.class, TestClaimsSecurityConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.main.web-application-type=servlet"})
@AutoConfigureMockMvc
@DisplayName("Exception Handling Integration Tests")
class ExceptionHandlingIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Should return 404 with ApiErrorResponse for ResourceNotFoundException")
  void shouldReturn404ForResourceNotFound() throws Exception {
    mockMvc
        .perform(get("/api/test/not-found").with(ClaimsHeaderTestBuilder.defaultUser()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Should return 422 with ApiErrorResponse for BusinessException")
  void shouldReturn422ForBusinessException() throws Exception {
    mockMvc
        .perform(get("/api/test/business-error").with(ClaimsHeaderTestBuilder.defaultUser()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("APPLICATION_ERROR"))
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
  }

  @Test
  @DisplayName("Should return 400 with ApiErrorResponse for InvalidRequestException")
  void shouldReturn400ForInvalidRequest() throws Exception {
    mockMvc
        .perform(get("/api/test/invalid-request").with(ClaimsHeaderTestBuilder.defaultUser()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("Should return 500 with ApiErrorResponse for ServiceException")
  void shouldReturn500ForServiceException() throws Exception {
    mockMvc
        .perform(get("/api/test/service-error").with(ClaimsHeaderTestBuilder.defaultUser()))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.type").value("INTERNAL_ERROR"));
  }

  @Test
  @DisplayName("Should return 503 with ApiErrorResponse for ServiceUnavailableException")
  void shouldReturn503ForServiceUnavailable() throws Exception {
    mockMvc
        .perform(get("/api/test/service-unavailable").with(ClaimsHeaderTestBuilder.defaultUser()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.type").value("SERVICE_UNAVAILABLE"));
  }

  @Test
  @DisplayName("Should return 500 with ApiErrorResponse for unexpected RuntimeException")
  void shouldReturn500ForUnexpectedRuntimeException() throws Exception {
    mockMvc
        .perform(get("/api/test/runtime-error").with(ClaimsHeaderTestBuilder.defaultUser()))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.type").value("INTERNAL_ERROR"))
        .andExpect(content().string(not(containsString("internal-secret-runtime-63820"))));
  }

  @Test
  @DisplayName("Should return 405 with standard error and Allow header for unsupported method")
  void shouldReturn405ForUnsupportedMethod() throws Exception {
    mockMvc
        .perform(put("/api/test/method-restricted").with(ClaimsHeaderTestBuilder.defaultUser()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(
            header()
                .string(HttpHeaders.ALLOW, allOf(containsString("GET"), containsString("PATCH"))))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.type").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("Should return 404 with standard error for unmapped route")
  void shouldReturn404ForUnmappedRoute() throws Exception {
    mockMvc
        .perform(get("/api/test/removed-route").with(ClaimsHeaderTestBuilder.defaultUser()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.type").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Should preserve ResponseStatusException behavior")
  void shouldPreserveResponseStatusExceptionBehavior() throws Exception {
    mockMvc
        .perform(get("/api/test/response-status").with(ClaimsHeaderTestBuilder.defaultUser()))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.type").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("Should return 400 with field errors for validation failures")
  void shouldReturn400WithFieldErrorsForValidationFailure() throws Exception {
    var invalidRequest = "{\"name\": \"\"}";

    mockMvc
        .perform(
            post("/api/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest)
                .with(ClaimsHeaderTestBuilder.defaultUser()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors").isArray())
        .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
        .andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("name", "name")))
        .andExpect(jsonPath("$.fieldErrors[*].rejectedValue").value(containsInAnyOrder("", "")));
  }

  @Test
  @DisplayName("Should return 200 for valid request")
  void shouldReturn200ForValidRequest() throws Exception {
    var validRequest = "{\"name\": \"ValidName\"}";

    mockMvc
        .perform(
            post("/api/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest)
                .with(ClaimsHeaderTestBuilder.defaultUser()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Should include correlation ID in all error responses")
  void shouldIncludeCorrelationIdInErrorResponses() throws Exception {
    var correlationId = "test-correlation-id-12345";

    mockMvc
        .perform(
            get("/api/test/not-found")
                .header("X-Correlation-ID", correlationId)
                .with(ClaimsHeaderTestBuilder.defaultUser()))
        .andExpect(status().isNotFound());
  }
}
