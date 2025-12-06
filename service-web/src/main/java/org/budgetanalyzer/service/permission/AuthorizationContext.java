package org.budgetanalyzer.service.permission;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Context information for authorization requests.
 *
 * <p>Contains the user identity and optional request metadata used for authorization decisions and
 * audit logging. The permission-service uses this context to evaluate permissions and record audit
 * trails.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Simple context with just user ID
 * var ctx = AuthorizationContext.of("auth0|123");
 *
 * // Full context from HTTP request
 * var ctx = AuthorizationContext.fromRequest("auth0|123", request);
 * }</pre>
 *
 * @param userId the unique identifier of the user (typically Auth0 subject claim)
 * @param clientIp the client IP address for audit logging (may be null)
 * @param correlationId the request correlation ID for tracing (may be null)
 * @param sourceService the name of the service making the request (may be null)
 */
public record AuthorizationContext(
    String userId, String clientIp, String correlationId, String sourceService) {

  /**
   * Creates an authorization context with just a user ID.
   *
   * <p>Use this factory method when request metadata is not available or not needed, such as in
   * background jobs or service-to-service calls.
   *
   * @param userId the unique identifier of the user
   * @return a new AuthorizationContext with only userId populated
   */
  public static AuthorizationContext of(String userId) {
    return new AuthorizationContext(userId, null, null, null);
  }

  /**
   * Creates an authorization context from an HTTP servlet request.
   *
   * <p>Extracts client IP (respecting X-Forwarded-For), correlation ID, and source service headers
   * from the request. Use this factory method in controller methods.
   *
   * @param userId the unique identifier of the user
   * @param request the HTTP servlet request
   * @return a new AuthorizationContext populated from the request
   */
  public static AuthorizationContext fromRequest(String userId, HttpServletRequest request) {
    return new AuthorizationContext(
        userId,
        extractClientIp(request),
        request.getHeader("X-Correlation-ID"),
        request.getHeader("X-Source-Service"));
  }

  /**
   * Extracts the client IP address from the request, respecting X-Forwarded-For header.
   *
   * @param request the HTTP servlet request
   * @return the client IP address
   */
  private static String extractClientIp(HttpServletRequest request) {
    var forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      // Take the first IP in the chain (original client)
      var firstIp = forwardedFor.split(",")[0].trim();
      if (!firstIp.isEmpty()) {
        return firstIp;
      }
    }
    return request.getRemoteAddr();
  }
}
