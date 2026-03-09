package org.budgetanalyzer.service.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication token populated from pre-validated claims headers injected by Envoy ext_authz.
 *
 * <p>After Phase 1 of the auth migration, Envoy validates sessions via ext_authz and injects claims
 * headers ({@code X-User-Id}, {@code X-Permissions}, {@code X-Roles}) into requests before they
 * reach backend services. This token represents the authenticated user identity extracted from
 * those headers.
 *
 * <p>Extends {@link AbstractAuthenticationToken} (same base class as {@code
 * JwtAuthenticationToken}) to ensure seamless integration with {@code @PreAuthorize}, {@code
 * hasRole()}, {@code hasAuthority()}, and {@code SecurityContextAuditorAware}.
 *
 * @see ClaimsHeaderAuthenticationFilter
 * @see ClaimsHeaderSecurityConfig
 */
public class ClaimsHeaderAuthenticationToken extends AbstractAuthenticationToken {

  private static final long serialVersionUID = 1L;

  private final String userId;
  private final Set<String> roles;

  /**
   * Creates an authenticated token from claims headers.
   *
   * @param userId the user ID from {@code X-User-Id} header
   * @param roles the raw role names (without {@code ROLE_} prefix)
   * @param authorities the granted authorities (permissions + ROLE_-prefixed roles)
   */
  public ClaimsHeaderAuthenticationToken(
      String userId, Set<String> roles, Collection<? extends GrantedAuthority> authorities) {
    super(Collections.unmodifiableList(new java.util.ArrayList<>(authorities)));
    this.userId = userId;
    this.roles = Collections.unmodifiableSet(roles);
    setAuthenticated(true);
  }

  /**
   * Returns the user ID. Keeps {@code SecurityContextAuditorAware} working unchanged.
   *
   * @return the user ID from the {@code X-User-Id} header
   */
  @Override
  public Object getPrincipal() {
    return userId;
  }

  /**
   * Returns empty string since authentication is performed by ext_authz, not this service.
   *
   * @return empty string
   */
  @Override
  public Object getCredentials() {
    return "";
  }

  /**
   * Returns the user ID. Keeps {@code SecurityContextAuditorAware} working unchanged.
   *
   * @return the user ID from the {@code X-User-Id} header
   */
  @Override
  public String getName() {
    return userId;
  }

  /**
   * Returns the raw role names without the {@code ROLE_} prefix.
   *
   * @return unmodifiable set of role names
   */
  public Set<String> getRoles() {
    return roles;
  }
}
