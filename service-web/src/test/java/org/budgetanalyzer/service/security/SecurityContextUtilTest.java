package org.budgetanalyzer.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.budgetanalyzer.service.security.test.JwtTestBuilder;

/**
 * Unit tests for {@link SecurityContextUtil}.
 *
 * <p>Verifies JWT claim extraction from the Spring Security context.
 */
class SecurityContextUtilTest {

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getCurrentUserId_shouldReturnSubjectFromJwt() {
    var jwt = JwtTestBuilder.user("auth0|123456789").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var userId = SecurityContextUtil.getCurrentUserId();

    assertThat(userId).isPresent().contains("auth0|123456789");
  }

  @Test
  void getCurrentUserId_shouldReturnEmptyWhenNoAuthentication() {
    SecurityContextHolder.clearContext();

    var userId = SecurityContextUtil.getCurrentUserId();

    assertThat(userId).isEmpty();
  }

  @Test
  void getCurrentUserEmail_shouldReturnEmailFromJwt() {
    var jwt = JwtTestBuilder.user("test-user").withClaim("email", "test@example.com").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var email = SecurityContextUtil.getCurrentUserEmail();

    assertThat(email).isPresent().contains("test@example.com");
  }

  @Test
  void getCurrentUserEmail_shouldReturnEmptyWhenEmailClaimMissing() {
    var jwt = JwtTestBuilder.user("test-user").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var email = SecurityContextUtil.getCurrentUserEmail();

    assertThat(email).isEmpty();
  }

  @Test
  void getCurrentUserEmail_shouldReturnEmptyWhenNoAuthentication() {
    SecurityContextHolder.clearContext();

    var email = SecurityContextUtil.getCurrentUserEmail();

    assertThat(email).isEmpty();
  }

  @Test
  void getAllClaims_shouldReturnAllJwtClaims() {
    var jwt =
        JwtTestBuilder.user("test-user")
            .withClaim("email", "test@example.com")
            .withClaim("organization_id", "org-123")
            .build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var claims = SecurityContextUtil.getAllClaims();

    assertThat(claims).isPresent();
    assertThat(claims.get())
        .containsEntry("sub", "test-user")
        .containsEntry("email", "test@example.com")
        .containsEntry("organization_id", "org-123");
  }

  @Test
  void getAllClaims_shouldReturnEmptyWhenNoAuthentication() {
    SecurityContextHolder.clearContext();

    var claims = SecurityContextUtil.getAllClaims();

    assertThat(claims).isEmpty();
  }

  @Test
  void logAuthenticationContext_shouldNotThrowExceptionWhenAuthenticated() {
    var jwt = JwtTestBuilder.user("test-user").withClaim("email", "test@example.com").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    // Should not throw
    SecurityContextUtil.logAuthenticationContext();
  }

  @Test
  void logAuthenticationContext_shouldNotThrowExceptionWhenNoAuthentication() {
    SecurityContextHolder.clearContext();

    // Should not throw
    SecurityContextUtil.logAuthenticationContext();
  }
}
