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
    var jwt = JwtTestBuilder.user("usr_abc123").withIdpSub("idp|123456789").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var userId = SecurityContextUtil.getCurrentUserId();

    assertThat(userId).isPresent().contains("usr_abc123");
  }

  @Test
  void getCurrentUserId_shouldFallbackToJwtSubWhenHeaderMissing() {
    // Set up request without header
    var request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // Set up JWT — sub is the internal user ID in gateway JWTs
    var jwt = JwtTestBuilder.user("usr_abc123").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var userId = SecurityContextUtil.getCurrentUserId();

    assertThat(userId).isPresent().contains("usr_abc123");
  }

  @Test
  void getCurrentUserId_shouldFallbackToJwtWhenHeaderBlank() {
    // Set up request with blank header
    var request = new MockHttpServletRequest();
    request.addHeader(SecurityContextUtil.INTERNAL_USER_ID_HEADER, "   ");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // Set up JWT
    var jwt = JwtTestBuilder.user("usr_abc123").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var userId = SecurityContextUtil.getCurrentUserId();

    assertThat(userId).isPresent().contains("usr_abc123");
  }

  @Test
  void getCurrentUserId_shouldReturnEmptyWhenNoHeaderAndNoAuthentication() {
    var request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    SecurityContextHolder.clearContext();

    var userId = SecurityContextUtil.getCurrentUserId();

    assertThat(userId).isEmpty();
  }

  // --- getIdpSub tests ---

  @Test
  void getIdpSub_shouldReturnIdpSubFromJwt() {
    var jwt = JwtTestBuilder.user("usr_abc123").withIdpSub("idp|123456789").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var idpSub = SecurityContextUtil.getIdpSub();

    assertThat(idpSub).isPresent().contains("idp|123456789");
  }

  @Test
  void getIdpSub_shouldReturnEmptyWhenNoAuthentication() {
    SecurityContextHolder.clearContext();

    var idpSub = SecurityContextUtil.getIdpSub();

    assertThat(idpSub).isEmpty();
  }

  @Test
  void getIdpSub_shouldReturnEmptyWhenIdpSubClaimMissing() {
    // Build a JWT without the idp_sub claim by overriding it with null via custom claim
    var jwt = JwtTestBuilder.user("usr_abc123").withClaim("idp_sub", null).build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var idpSub = SecurityContextUtil.getIdpSub();

    assertThat(idpSub).isEmpty();
  }

  @Test
  void getIdpSub_shouldIgnoreHeader() {
    // Set up request with X-Internal-User-Id header
    var request = new MockHttpServletRequest();
    request.addHeader(SecurityContextUtil.INTERNAL_USER_ID_HEADER, "usr_abc123");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // Set up JWT with idp_sub
    var jwt = JwtTestBuilder.user("usr_abc123").withIdpSub("idp|123456789").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    // getIdpSub should return idp_sub, ignoring header
    var idpSub = SecurityContextUtil.getIdpSub();

    assertThat(idpSub).isPresent().contains("idp|123456789");
  }

  @Test
  void getCurrentUserEmail_shouldReturnEmailFromJwt() {
    var jwt = JwtTestBuilder.user("usr_test123").withClaim("email", "test@example.com").build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var email = SecurityContextUtil.getCurrentUserEmail();

    assertThat(email).isPresent().contains("test@example.com");
  }

  @Test
  void getCurrentUserEmail_shouldReturnEmptyWhenEmailClaimMissing() {
    var jwt = JwtTestBuilder.user("usr_test123").build();
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
        JwtTestBuilder.user("usr_test123")
            .withClaim("email", "test@example.com")
            .withClaim("organization_id", "org-123")
            .build();
    var authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var claims = SecurityContextUtil.getAllClaims();

    assertThat(claims).isPresent();
    assertThat(claims.get())
        .containsEntry("sub", "usr_test123")
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
    var jwt = JwtTestBuilder.user("usr_test123").withClaim("email", "test@example.com").build();
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
