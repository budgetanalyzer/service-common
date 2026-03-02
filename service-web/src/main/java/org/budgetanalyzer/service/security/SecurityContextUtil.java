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
   * <p>This header contains the vendor-independent internal user ID, decoupled from the identity
   * provider. Session-gateway resolves identity provider sub → internal user ID at login and adds
   * this header to all proxied requests.
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
   *   <li>X-Internal-User-Id header (vendor-independent internal user ID from gateway)
   *   <li>JWT 'sub' claim (the internal user ID in gateway-minted JWTs)
   * </ol>
   *
   * <p>The header approach is preferred for explicit routing. The JWT sub fallback works because
   * gateway-minted JWTs already use the internal user ID as the subject claim.
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

    // Priority 2: Fallback to JWT sub claim (internal user ID in gateway JWTs)
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      var jwt = jwtAuth.getToken();
      var userId = jwt.getSubject();
      logger.trace("Extracted user ID from JWT sub (fallback): {}", userId);
      return Optional.ofNullable(userId);
    }

    logger.trace("No user ID found in header or JWT");
    return Optional.empty();
  }

  /**
   * Extracts the identity provider subject identifier from the JWT's idp_sub claim.
   *
   * <p>In gateway-minted JWTs, the original identity provider subject is stored in the {@code
   * idp_sub} claim, while {@code sub} contains the internal user ID. Use this method when you
   * specifically need the identity provider identifier (e.g., for IdP management API calls). For
   * general user identification, prefer {@link #getCurrentUserId()}.
   *
   * @return Optional containing the IdP sub (idp_sub claim) if authenticated, empty otherwise
   */
  public static Optional<String> getIdpSub() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      var jwt = jwtAuth.getToken();
      var idpSub = jwt.getClaimAsString("idp_sub");
      logger.trace("Extracted IdP sub from JWT idp_sub: {}", idpSub);
      return Optional.ofNullable(idpSub);
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
