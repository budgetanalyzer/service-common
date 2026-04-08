package org.budgetanalyzer.service.security;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for extracting user information from the Spring Security context.
 *
 * <p>This utility extracts user identity from the {@link ClaimsHeaderAuthenticationToken} populated
 * by the {@link ClaimsHeaderAuthenticationFilter}. Used for logging, audit purposes, and
 * user-specific business logic.
 *
 * <p><b>Common use cases:</b>
 *
 * <ul>
 *   <li>Logging user actions for audit trails
 *   <li>Extracting user ID for user-specific queries
 *   <li>Checking user roles or authorities for authorization decisions
 * </ul>
 *
 * <p><b>Thread safety:</b> This utility is thread-safe as it reads from Spring's
 * SecurityContextHolder which uses ThreadLocal storage.
 */
public class SecurityContextUtil {

  private static final Logger logger = LoggerFactory.getLogger(SecurityContextUtil.class);

  private SecurityContextUtil() {
    // Utility class - prevent instantiation
  }

  /**
   * Extracts the user ID from the current security context.
   *
   * <p>The user ID is extracted from {@code Authentication.getName()}, which returns the value from
   * the {@code X-User-Id} header injected by trusted ingress external auth.
   *
   * @return Optional containing the user ID if authenticated, empty otherwise
   */
  public static Optional<String> getCurrentUserId() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      logger.trace("No authenticated user in security context");
      return Optional.empty();
    }

    var name = authentication.getName();
    if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
      logger.trace("No user ID found in authentication");
      return Optional.empty();
    }

    logger.trace("Extracted user ID from authentication: {}", name);
    return Optional.of(name);
  }

  /**
   * Checks whether the currently authenticated user has the specified role.
   *
   * <p>Roles are stored as Spring Security authorities with the "ROLE_" prefix (e.g.,
   * "ROLE_ADMIN"). This method handles the prefix automatically — pass the role name without it.
   *
   * @param role the role name without the "ROLE_" prefix (e.g., "ADMIN")
   * @return true if the user has the specified role, false otherwise
   */
  public static boolean hasRole(String role) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
  }

  /**
   * Checks whether the currently authenticated user has the specified authority.
   *
   * <p>Authorities are matched verbatim — no prefix is added. This is the imperative equivalent of
   * Spring Security's {@code hasAuthority(...)} SpEL expression used in {@code @PreAuthorize}, and
   * is intended for fine-grained permission checks inside controller or service code (e.g., "does
   * the caller have the {@code transactions:read:any} permission so we can skip the owner
   * filter?").
   *
   * @param authority the authority name to check (e.g., "transactions:read:any")
   * @return true if the user has the specified authority, false otherwise
   */
  public static boolean hasAuthority(String authority) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authority == null) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .anyMatch(granted -> authority.equals(granted.getAuthority()));
  }

  /**
   * Logs user authentication context for audit purposes.
   *
   * <p>Logs the user ID and granted authorities. This provides visibility into who is making API
   * requests.
   */
  public static void logAuthenticationContext() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null && authentication.isAuthenticated()) {
      var userId = authentication.getName();
      var authorities = authentication.getAuthorities();
      logger.info("Authenticated user - ID: {}, Authorities: {}", userId, authorities);
    } else {
      logger.debug("No authentication in security context");
    }
  }
}
