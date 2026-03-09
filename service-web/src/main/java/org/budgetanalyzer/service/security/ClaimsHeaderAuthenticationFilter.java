package org.budgetanalyzer.service.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that reads pre-validated claims headers and populates the SecurityContext.
 *
 * <p>After Envoy ext_authz validates the session, it injects claims headers into the request. This
 * filter extracts those headers and creates a {@link ClaimsHeaderAuthenticationToken} in the
 * SecurityContext, enabling standard Spring Security authorization ({@code @PreAuthorize}, {@code
 * hasRole()}, {@code hasAuthority()}).
 *
 * <p>This filter is <b>not</b> a {@code @Component} — it is registered inside the {@link
 * ClaimsHeaderSecurityConfig} SecurityFilterChain via {@code addFilterBefore()}.
 *
 * @see ClaimsHeaderAuthenticationToken
 * @see ClaimsHeaderSecurityConfig
 */
public class ClaimsHeaderAuthenticationFilter extends OncePerRequestFilter {

  /** Header containing the authenticated user's ID. */
  public static final String X_USER_ID_HEADER = "X-User-Id";

  /** Header containing comma-separated permissions. */
  public static final String X_PERMISSIONS_HEADER = "X-Permissions";

  /** Header containing comma-separated roles. */
  public static final String X_ROLES_HEADER = "X-Roles";

  private static final Logger logger =
      LoggerFactory.getLogger(ClaimsHeaderAuthenticationFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    var userId = request.getHeader(X_USER_ID_HEADER);

    if (userId == null || userId.isBlank()) {
      logger.trace("No {} header present, skipping authentication", X_USER_ID_HEADER);
      filterChain.doFilter(request, response);
      return;
    }

    var permissions = parseCommaSeparated(request.getHeader(X_PERMISSIONS_HEADER));
    var roles = parseCommaSeparated(request.getHeader(X_ROLES_HEADER));

    List<GrantedAuthority> authorities = new ArrayList<>();
    for (var permission : permissions) {
      authorities.add(new SimpleGrantedAuthority(permission));
    }
    for (var role : roles) {
      authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
    }

    var roleSet = new LinkedHashSet<>(roles);
    var authentication = new ClaimsHeaderAuthenticationToken(userId, roleSet, authorities);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    logger.trace(
        "Authenticated user {} with {} permissions and {} roles",
        userId,
        permissions.size(),
        roles.size());

    filterChain.doFilter(request, response);
  }

  private static List<String> parseCommaSeparated(String headerValue) {
    if (headerValue == null || headerValue.isBlank()) {
      return List.of();
    }
    return Arrays.stream(headerValue.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }
}
