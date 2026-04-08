package org.budgetanalyzer.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import org.budgetanalyzer.service.security.ClaimsHeaderAuthenticationFilter;
import org.budgetanalyzer.service.security.test.ClaimsHeaderTestBuilder;

/**
 * Integration tests verifying reactive HTTP security rules in {@link
 * org.budgetanalyzer.service.security.ReactiveClaimsHeaderSecurityConfig}.
 */
@SpringBootTest(
    classes = ReactiveTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.main.web-application-type=reactive"})
@AutoConfigureWebTestClient
@DisplayName("Reactive Security Rules Integration Tests")
class ReactiveSecurityRulesIntegrationTest {

  @Autowired private WebTestClient webTestClient;

  @Test
  @DisplayName("should require authentication for protected endpoints")
  void shouldRequireAuthenticationForProtectedEndpoints() {
    webTestClient
        .get()
        .uri("/api/test/not-found")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("UNAUTHORIZED");
  }

  @Test
  @DisplayName("should allow authenticated access to protected endpoints")
  void shouldAllowAuthenticatedAccessToProtectedEndpoints() {
    webTestClient
        .get()
        .uri("/api/test/not-found")
        .headers(
            headers -> ClaimsHeaderTestBuilder.defaultUser().buildHeaders().forEach(headers::add))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  @DisplayName("should require authentication for internal endpoints")
  void shouldRequireAuthenticationForInternalEndpoints() {
    webTestClient
        .get()
        .uri("/internal/test")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("UNAUTHORIZED");
  }

  @Test
  @DisplayName("should allow authenticated access to internal endpoints")
  void shouldAllowAuthenticatedAccessToInternalEndpoints() {
    webTestClient
        .get()
        .uri("/internal/test")
        .headers(
            headers -> ClaimsHeaderTestBuilder.defaultUser().buildHeaders().forEach(headers::add))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).isEqualTo("internal-ok"));
  }

  @Test
  @DisplayName("should reject malformed claims headers")
  void shouldRejectMalformedClaimsHeaders() {
    webTestClient
        .get()
        .uri("/api/test/not-found")
        .header(ClaimsHeaderAuthenticationFilter.X_PERMISSIONS_HEADER, "transactions:read")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("UNAUTHORIZED");
  }

  @Test
  @DisplayName("should allow access when claims satisfy reactive method security")
  void shouldAllowAccessWhenClaimsSatisfyReactiveMethodSecurity() {
    webTestClient
        .get()
        .uri("/api/reactive-security/transactions-write")
        .headers(
            headers ->
                ClaimsHeaderTestBuilder.user("usr_reactive_writer")
                    .withPermissions("transactions:write")
                    .buildHeaders()
                    .forEach(headers::add))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).isEqualTo("method-security-ok"));
  }

  @Test
  @DisplayName(
      "should return forbidden ApiErrorResponse when reactive method security denies access")
  void shouldReturnForbiddenApiErrorResponseWhenReactiveMethodSecurityDeniesAccess() {
    webTestClient
        .get()
        .uri("/api/reactive-security/transactions-write")
        .headers(
            headers ->
                ClaimsHeaderTestBuilder.user("usr_reactive_reader")
                    .withPermissions("currencies:read")
                    .buildHeaders()
                    .forEach(headers::add))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("FORBIDDEN")
        .jsonPath("$.message")
        .isEqualTo("You do not have permission to perform this action");
  }
}
