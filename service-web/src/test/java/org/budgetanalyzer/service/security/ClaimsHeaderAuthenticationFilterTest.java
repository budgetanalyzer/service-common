package org.budgetanalyzer.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link ClaimsHeaderAuthenticationFilter}.
 *
 * <p>Verifies claims header parsing and SecurityContext population.
 */
class ClaimsHeaderAuthenticationFilterTest {

  private final ClaimsHeaderAuthenticationFilter filter = new ClaimsHeaderAuthenticationFilter();

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldPopulateSecurityContextWhenAllHeadersPresent() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "usr_abc123");
    request.addHeader("X-Permissions", "transactions:read,accounts:read");
    request.addHeader("X-Roles", "USER");

    filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isNotNull();
    assertThat(authentication.getName()).isEqualTo("usr_abc123");
    assertThat(authentication.isAuthenticated()).isTrue();
    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("transactions:read", "accounts:read", "ROLE_USER");
  }

  @Test
  void shouldPopulateSecurityContextWithOnlyUserId() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "usr_abc123");

    filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isNotNull();
    assertThat(authentication.getName()).isEqualTo("usr_abc123");
    assertThat(authentication.getAuthorities()).isEmpty();
  }

  @Test
  void shouldSkipAuthWhenUserIdMissing() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Permissions", "transactions:read");

    filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void shouldSkipAuthWhenUserIdBlank() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "   ");

    filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void shouldParseCommaSeparatedPermissionsCorrectly() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "usr_abc123");
    request.addHeader("X-Permissions", "transactions:read,accounts:read,budgets:write");

    filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("transactions:read", "accounts:read", "budgets:write");
  }

  @Test
  void shouldParseCommaSeparatedRolesCorrectly() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "usr_abc123");
    request.addHeader("X-Roles", "ADMIN,USER");

    filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN", "ROLE_USER");
  }

  @Test
  void shouldTrimWhitespaceInParsedValues() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "usr_abc123");
    request.addHeader("X-Permissions", " transactions:read , accounts:read ");
    request.addHeader("X-Roles", " ADMIN , USER ");

    filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("transactions:read", "accounts:read", "ROLE_ADMIN", "ROLE_USER");
  }

  @Test
  void shouldHandleSingleValueWithNoComma() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "usr_abc123");
    request.addHeader("X-Permissions", "transactions:read");
    request.addHeader("X-Roles", "USER");

    filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("transactions:read", "ROLE_USER");
  }

  @Test
  void shouldReturnUserIdFromGetNameForSecurityContextAuditorAwareCompatibility() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "usr_abc123");

    filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getName()).isEqualTo("usr_abc123");
  }

  @Test
  void shouldContinueFilterChainRegardlessOfAuthenticationResult() throws Exception {
    var request = new MockHttpServletRequest();
    var chain = new MockFilterChain();

    // No headers — should still continue filter chain
    filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void shouldExposeRolesViaToken() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "usr_abc123");
    request.addHeader("X-Roles", "ADMIN,USER");

    filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isInstanceOf(ClaimsHeaderAuthenticationToken.class);
    var token = (ClaimsHeaderAuthenticationToken) authentication;
    assertThat(token.getRoles()).containsExactlyInAnyOrder("ADMIN", "USER");
  }
}
