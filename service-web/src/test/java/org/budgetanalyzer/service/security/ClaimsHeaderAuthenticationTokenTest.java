package org.budgetanalyzer.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Unit tests for {@link ClaimsHeaderAuthenticationToken}.
 *
 * <p>Verifies the authentication token correctly stores user identity and authorities.
 */
class ClaimsHeaderAuthenticationTokenTest {

  @Test
  void constructor_shouldSetAuthoritiesCorrectly() {
    var authorities =
        List.<GrantedAuthority>of(
            new SimpleGrantedAuthority("transactions:read"),
            new SimpleGrantedAuthority("ROLE_USER"));

    var token = new ClaimsHeaderAuthenticationToken("usr_abc123", Set.of("USER"), authorities);

    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("transactions:read", "ROLE_USER");
  }

  @Test
  void getName_shouldReturnUserId() {
    var token = new ClaimsHeaderAuthenticationToken("usr_abc123", Set.of("USER"), List.of());

    assertThat(token.getName()).isEqualTo("usr_abc123");
  }

  @Test
  void getPrincipal_shouldReturnUserId() {
    var token = new ClaimsHeaderAuthenticationToken("usr_abc123", Set.of("USER"), List.of());

    assertThat(token.getPrincipal()).isEqualTo("usr_abc123");
  }

  @Test
  void getCredentials_shouldReturnEmptyString() {
    var token = new ClaimsHeaderAuthenticationToken("usr_abc123", Set.of("USER"), List.of());

    assertThat(token.getCredentials()).isEqualTo("");
  }

  @Test
  void isAuthenticated_shouldReturnTrue() {
    var token = new ClaimsHeaderAuthenticationToken("usr_abc123", Set.of("USER"), List.of());

    assertThat(token.isAuthenticated()).isTrue();
  }

  @Test
  void getRoles_shouldReturnRoleSet() {
    var token =
        new ClaimsHeaderAuthenticationToken("usr_abc123", Set.of("ADMIN", "USER"), List.of());

    assertThat(token.getRoles()).containsExactlyInAnyOrder("ADMIN", "USER");
  }

  @Test
  void getRoles_shouldReturnUnmodifiableSet() {
    var token = new ClaimsHeaderAuthenticationToken("usr_abc123", Set.of("USER"), List.of());

    assertThatThrownBy(() -> token.getRoles().add("ADMIN"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void getAuthorities_shouldReturnUnmodifiableCollection() {
    var authorities = List.<GrantedAuthority>of(new SimpleGrantedAuthority("transactions:read"));

    var token = new ClaimsHeaderAuthenticationToken("usr_abc123", Set.of(), authorities);

    assertThatThrownBy(() -> token.getAuthorities().add(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void constructor_shouldHandleEmptyPermissionsAndRoles() {
    var token = new ClaimsHeaderAuthenticationToken("usr_abc123", Set.of(), List.of());

    assertThat(token.getAuthorities()).isEmpty();
    assertThat(token.getRoles()).isEmpty();
    assertThat(token.getName()).isEqualTo("usr_abc123");
    assertThat(token.isAuthenticated()).isTrue();
  }
}
