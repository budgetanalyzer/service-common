package org.budgetanalyzer.service.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import org.budgetanalyzer.service.security.test.ClaimsHeaderTestBuilder;
import org.budgetanalyzer.service.security.test.TestClaimsSecurityConfig;

/**
 * Integration tests verifying HTTP security rules in {@link
 * org.budgetanalyzer.service.security.ClaimsHeaderSecurityConfig}.
 */
@SpringBootTest(
    classes = {ServletTestApplication.class, TestClaimsSecurityConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.main.web-application-type=servlet"})
@AutoConfigureMockMvc
@DisplayName("Security Rules Integration Tests")
class SecurityRulesIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Nested
  @DisplayName("Internal endpoints (/internal/**)")
  class InternalEndpointTests {

    @Test
    @DisplayName("should require authentication")
    void shouldRequireAuthenticationForInternalEndpoints() throws Exception {
      mockMvc
          .perform(get("/internal/test"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.type").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("should allow authenticated access")
    void shouldAllowAuthenticatedAccessToInternalEndpoints() throws Exception {
      mockMvc
          .perform(get("/internal/test").with(ClaimsHeaderTestBuilder.defaultUser()))
          .andExpect(status().isOk())
          .andExpect(content().string("internal-ok"));
    }

    @Test
    @DisplayName("should not persist authentication across requests")
    void shouldNotPersistAuthenticationAcrossRequests() throws Exception {
      var mockHttpSession = new MockHttpSession();

      mockMvc
          .perform(
              get("/internal/test")
                  .session(mockHttpSession)
                  .with(ClaimsHeaderTestBuilder.defaultUser()))
          .andExpect(status().isOk());

      mockMvc
          .perform(get("/internal/test").session(mockHttpSession))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.type").value("UNAUTHORIZED"));
    }
  }

  @Nested
  @DisplayName("Protected endpoints")
  class ProtectedEndpointTests {

    @Test
    @DisplayName("should require authentication for non-internal endpoints")
    void shouldRequireAuthenticationForNonInternalEndpoints() throws Exception {
      mockMvc
          .perform(get("/api/test/not-found"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.type").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("should allow authenticated access to non-internal endpoints")
    void shouldAllowAuthenticatedAccessToNonInternalEndpoints() throws Exception {
      mockMvc
          .perform(get("/api/test/not-found").with(ClaimsHeaderTestBuilder.defaultUser()))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should reject malformed claims headers")
    void shouldRejectMalformedClaimsHeaders() throws Exception {
      mockMvc
          .perform(get("/api/test/not-found").header("X-Permissions", "transactions:read"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.type").value("UNAUTHORIZED"));
    }
  }

  @Nested
  @DisplayName("Health endpoints")
  class HealthEndpointTests {

    @Test
    @DisplayName("should permit health endpoint without authentication")
    void shouldPermitHealthEndpointWithoutAuthentication() throws Exception {
      mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
  }
}
