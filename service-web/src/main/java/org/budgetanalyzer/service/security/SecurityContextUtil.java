package org.budgetanalyzer.service.security;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Utility class for extracting user information from the Spring Security context.
 *
 * <p>This utility extracts user identity from JWT tokens validated by Spring Security OAuth2
 * Resource Server. Used for logging, audit purposes, and user-specific business logic.
 *
 * <p><b>Common use cases:</b>
 *
 * <ul>
 *   <li>Logging user actions for audit trails
 *   <li>Extracting user ID for user-specific queries
 *   <li>Getting user email for notifications
 *   <li>Reading custom claims for multi-tenancy or organization context
 * </ul>
 *
 * <p><b>Thread safety:</b> This utility is thread-safe as it reads from Spring's
 * SecurityContextHolder which uses ThreadLocal storage.
 */
public class SecurityContextUtil {

  /**
   * Header name for the internal user ID passed by session-gateway.
   *
   * <p>This header contains the vendor-independent user ID from permission-service, decoupled from
   * the identity provider (e.g., Auth0). Session-gateway resolves Auth0 sub → internal user ID at
   * login and adds this header to all proxied requests.
   */
  public static final String INTERNAL_USER_ID_HEADER = "X-Internal-User-Id";

  private static final Logger logger = LoggerFactory.getLogger(SecurityContextUtil.class);

  private SecurityContextUtil() {
    // Utility class - prevent instantiation
  }

  /**
   * Extracts the internal user ID from the current request.
   *
   * <p>The user ID is extracted in the following priority order:
   *
   * <ol>
   *   <li>X-Internal-User-Id header (vendor-independent ID from permission-service)
   *   <li>JWT 'sub' claim (fallback for backwards compatibility during migration)
   * </ol>
   *
   * <p>The header approach is preferred because it decouples services from the identity provider.
   * The JWT fallback ensures services work during rolling deployment when session-gateway hasn't
   * been updated yet, or for requests that bypass session-gateway.
   *
   * @return Optional containing the user ID if available, empty otherwise
   */
  public static Optional<String> getCurrentUserId() {
    // Priority 1: Check X-Internal-User-Id header from session-gateway
    var requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes instanceof ServletRequestAttributes servletAttrs) {
      var request = servletAttrs.getRequest();
      var internalUserId = request.getHeader(INTERNAL_USER_ID_HEADER);
      if (internalUserId != null && !internalUserId.isBlank()) {
        logger.trace(
            "Extracted user ID from {} header: {}", INTERNAL_USER_ID_HEADER, internalUserId);
        return Optional.of(internalUserId);
      }
    }

    // Priority 2: Fallback to JWT sub claim for backwards compatibility
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      var jwt = jwtAuth.getToken();
      var userId = jwt.getSubject(); // Extract 'sub' claim (Auth0 identifier)
      logger.trace("Extracted user ID from JWT sub (fallback): {}", userId);
      return Optional.ofNullable(userId);
    }

    logger.trace("No user ID found in header or JWT");
    return Optional.empty();
  }

  /**
   * Extracts the Auth0 subject identifier from the JWT.
   *
   * <p>Use this method when you specifically need the Auth0 identifier (e.g., for Auth0 Management
   * API calls). For general user identification, prefer {@link #getCurrentUserId()} which returns
   * the vendor-independent internal user ID.
   *
   * @return Optional containing the Auth0 sub claim if authenticated, empty otherwise
   */
  public static Optional<String> getAuth0Sub() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      var jwt = jwtAuth.getToken();
      var auth0Sub = jwt.getSubject();
      logger.trace("Extracted Auth0 sub from JWT: {}", auth0Sub);
      return Optional.ofNullable(auth0Sub);
    }

    logger.trace("No JWT authentication found in security context");
    return Optional.empty();
  }

  /**
   * Extracts the user's email from the current security context.
   *
   * <p>The email is extracted from the JWT 'email' claim if present.
   *
   * @return Optional containing the user email if present, empty otherwise
   */
  public static Optional<String> getCurrentUserEmail() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      var jwt = jwtAuth.getToken();
      var email = jwt.getClaimAsString("email");
      logger.trace("Extracted user email from JWT: {}", email);
      return Optional.ofNullable(email);
    }

    return Optional.empty();
  }

  /**
   * Extracts all JWT claims from the current security context.
   *
   * <p>Useful for debugging and understanding what claims are available in the JWT, or for
   * extracting custom claims specific to your application.
   *
   * @return Optional containing all JWT claims if authenticated, empty otherwise
   */
  public static Optional<Map<String, Object>> getAllClaims() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      var jwt = jwtAuth.getToken();
      var claims = jwt.getClaims();

      logger.trace("JWT claims: {}", claims);
      return Optional.of(claims);
    }

    return Optional.empty();
  }

  /**
   * Logs user authentication context for audit purposes.
   *
   * <p>Logs the user ID, email, and granted authorities from the JWT. This provides visibility into
   * who is making API requests.
   */
  public static void logAuthenticationContext() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      var jwt = jwtAuth.getToken();
      var userId = jwt.getSubject();
      var email = jwt.getClaimAsString("email");
      var authorities = authentication.getAuthorities();

      logger.info(
          "Authenticated user - ID: {}, Email: {}, Authorities: {}", userId, email, authorities);
    } else if (authentication != null) {
      logger.info("Authenticated principal (non-JWT): {}", authentication.getName());
    } else {
      logger.debug("No authentication in security context");
    }
  }
}
