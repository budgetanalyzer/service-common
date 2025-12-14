package org.budgetanalyzer.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    RequestContextHolder.resetRequestAttributes();
  }

  // --- getCurrentUserId tests ---

  @Test
  void getCurrentUserId_shouldReturnInternalUserIdFromHeader() {
    // Set up request with X-Internal-User-Id header
    var request = new MockHttpServletRequest();
    request.addHeader(SecurityContextUtil.INTERNAL_USER_ID_HEADER, "usr_abc123");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // Also set up JWT (should be ignored when header is present)
    var jwt = JwtTestBuilder.user("auth0|123456789").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var userId = SecurityContextUtil.getCurrentUserId();

    assertThat(userId).isPresent().contains("usr_abc123");
  }

  @Test
  void getCurrentUserId_shouldFallbackToJwtWhenHeaderMissing() {
    // Set up request without header
    var request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // Set up JWT
    var jwt = JwtTestBuilder.user("auth0|123456789").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var userId = SecurityContextUtil.getCurrentUserId();

    assertThat(userId).isPresent().contains("auth0|123456789");
  }

  @Test
  void getCurrentUserId_shouldFallbackToJwtWhenHeaderBlank() {
    // Set up request with blank header
    var request = new MockHttpServletRequest();
    request.addHeader(SecurityContextUtil.INTERNAL_USER_ID_HEADER, "   ");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // Set up JWT
    var jwt = JwtTestBuilder.user("auth0|123456789").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var userId = SecurityContextUtil.getCurrentUserId();

    assertThat(userId).isPresent().contains("auth0|123456789");
  }

  @Test
  void getCurrentUserId_shouldReturnEmptyWhenNoHeaderAndNoAuthentication() {
    var request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    SecurityContextHolder.clearContext();

    var userId = SecurityContextUtil.getCurrentUserId();

    assertThat(userId).isEmpty();
  }

  // --- getAuth0Sub tests ---

  @Test
  void getAuth0Sub_shouldReturnSubjectFromJwt() {
    var jwt = JwtTestBuilder.user("auth0|123456789").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var auth0Sub = SecurityContextUtil.getAuth0Sub();

    assertThat(auth0Sub).isPresent().contains("auth0|123456789");
  }

  @Test
  void getAuth0Sub_shouldReturnEmptyWhenNoAuthentication() {
    SecurityContextHolder.clearContext();

    var auth0Sub = SecurityContextUtil.getAuth0Sub();

    assertThat(auth0Sub).isEmpty();
  }

  @Test
  void getAuth0Sub_shouldIgnoreHeader() {
    // Set up request with X-Internal-User-Id header
    var request = new MockHttpServletRequest();
    request.addHeader(SecurityContextUtil.INTERNAL_USER_ID_HEADER, "usr_abc123");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // Set up JWT with different sub
    var jwt = JwtTestBuilder.user("auth0|123456789").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    // getAuth0Sub should always return JWT sub, ignoring header
    var auth0Sub = SecurityContextUtil.getAuth0Sub();

    assertThat(auth0Sub).isPresent().contains("auth0|123456789");
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
