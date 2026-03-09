package org.budgetanalyzer.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link SecurityContextUtil}.
 *
 * <p>Verifies user information extraction from the Spring Security context.
 */
class SecurityContextUtilTest {

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
  }

  // --- getCurrentUserId tests ---

  @Test
  void getCurrentUserId_shouldReturnUserIdFromAuthentication() {
    var authentication =
        new ClaimsHeaderAuthenticationToken(
            "usr_abc123", Set.of("USER"), List.of(new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var userId = SecurityContextUtil.getCurrentUserId();

    assertThat(userId).isPresent().contains("usr_abc123");
  }

  @Test
  void getCurrentUserId_shouldReturnEmptyWhenNoAuthentication() {
    SecurityContextHolder.clearContext();

    var userId = SecurityContextUtil.getCurrentUserId();

    assertThat(userId).isEmpty();
  }

  @Test
  void getCurrentUserId_shouldReturnEmptyForAnonymousUser() {
    var authentication = new ClaimsHeaderAuthenticationToken("anonymousUser", Set.of(), List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);

    var userId = SecurityContextUtil.getCurrentUserId();

    assertThat(userId).isEmpty();
  }

  // --- hasRole tests ---

  @Test
  void hasRole_shouldReturnTrueWhenUserHasRole() {
    var authentication =
        new ClaimsHeaderAuthenticationToken(
            "usr_abc123", Set.of("ADMIN"), List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    SecurityContextHolder.getContext().setAuthentication(authentication);

    assertThat(SecurityContextUtil.hasRole("ADMIN")).isTrue();
  }

  @Test
  void hasRole_shouldReturnFalseWhenUserLacksRole() {
    var authentication =
        new ClaimsHeaderAuthenticationToken(
            "usr_abc123", Set.of("USER"), List.of(new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContextHolder.getContext().setAuthentication(authentication);

    assertThat(SecurityContextUtil.hasRole("ADMIN")).isFalse();
  }

  @Test
  void hasRole_shouldReturnFalseWhenNoAuthentication() {
    SecurityContextHolder.clearContext();

    assertThat(SecurityContextUtil.hasRole("ADMIN")).isFalse();
  }

  // --- logAuthenticationContext tests ---

  @Test
  void logAuthenticationContext_shouldNotThrowWhenAuthenticated() {
    var authentication =
        new ClaimsHeaderAuthenticationToken(
            "usr_abc123",
            Set.of("USER"),
            List.of(
                new SimpleGrantedAuthority("transactions:read"),
                new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContextHolder.getContext().setAuthentication(authentication);

    SecurityContextUtil.logAuthenticationContext();
  }

  @Test
  void logAuthenticationContext_shouldNotThrowWhenNoAuthentication() {
    SecurityContextHolder.clearContext();

    SecurityContextUtil.logAuthenticationContext();
  }
}
