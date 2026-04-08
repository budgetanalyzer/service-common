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

  // --- hasAuthority tests ---

  @Test
  void hasAuthority_shouldReturnTrueWhenUserHasAuthority() {
    var authentication =
        new ClaimsHeaderAuthenticationToken(
            "usr_abc123",
            Set.of("USER"),
            List.of(
                new SimpleGrantedAuthority("transactions:read"),
                new SimpleGrantedAuthority("transactions:read:any")));
    SecurityContextHolder.getContext().setAuthentication(authentication);

    assertThat(SecurityContextUtil.hasAuthority("transactions:read:any")).isTrue();
  }

  @Test
  void hasAuthority_shouldReturnFalseWhenUserLacksAuthority() {
    var authentication =
        new ClaimsHeaderAuthenticationToken(
            "usr_abc123", Set.of("USER"), List.of(new SimpleGrantedAuthority("transactions:read")));
    SecurityContextHolder.getContext().setAuthentication(authentication);

    assertThat(SecurityContextUtil.hasAuthority("transactions:read:any")).isFalse();
  }

  @Test
  void hasAuthority_shouldReturnFalseWhenNoAuthentication() {
    SecurityContextHolder.clearContext();

    assertThat(SecurityContextUtil.hasAuthority("transactions:read:any")).isFalse();
  }

  @Test
  void hasAuthority_shouldReturnFalseWhenAuthorityIsNull() {
    var authentication =
        new ClaimsHeaderAuthenticationToken(
            "usr_abc123",
            Set.of("USER"),
            List.of(new SimpleGrantedAuthority("transactions:read:any")));
    SecurityContextHolder.getContext().setAuthentication(authentication);

    assertThat(SecurityContextUtil.hasAuthority(null)).isFalse();
  }

  @Test
  void hasAuthority_shouldNotAddRolePrefix() {
    var authentication =
        new ClaimsHeaderAuthenticationToken(
            "usr_abc123", Set.of("ADMIN"), List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    SecurityContextHolder.getContext().setAuthentication(authentication);

    assertThat(SecurityContextUtil.hasAuthority("ADMIN")).isFalse();
    assertThat(SecurityContextUtil.hasAuthority("ROLE_ADMIN")).isTrue();
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
