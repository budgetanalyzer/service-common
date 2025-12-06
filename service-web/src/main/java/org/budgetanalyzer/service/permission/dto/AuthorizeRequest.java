package org.budgetanalyzer.service.permission.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request DTO for the permission-service /v1/authorize endpoint.
 *
 * <p>Used by {@link org.budgetanalyzer.service.permission.HttpPermissionClient} to check if a user
 * can perform an action on a specific resource.
 *
 * @param userId the unique identifier of the user (typically Auth0 subject claim)
 * @param action the action to perform (e.g., "read", "write", "delete")
 * @param resourceType the type of resource (e.g., "transaction", "currency")
 * @param resourceId the unique identifier of the resource (null for type-level checks)
 * @param ownerId the user ID of the resource owner (null for type-level checks)
 * @param context additional context for audit logging
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthorizeRequest(
    String userId,
    String action,
    String resourceType,
    String resourceId,
    String ownerId,
    AuthorizeRequestContext context) {

  /**
   * Creates a type-level authorization request (no specific resource).
   *
   * @param userId the user ID
   * @param action the action to perform
   * @param resourceType the resource type
   * @param context the request context
   * @return a new AuthorizeRequest for type-level authorization
   */
  public static AuthorizeRequest forType(
      String userId, String action, String resourceType, AuthorizeRequestContext context) {
    return new AuthorizeRequest(userId, action, resourceType, null, null, context);
  }

  /**
   * Creates a resource-level authorization request.
   *
   * @param userId the user ID
   * @param action the action to perform
   * @param resourceType the resource type
   * @param resourceId the resource ID
   * @param ownerId the resource owner ID
   * @param context the request context
   * @return a new AuthorizeRequest for resource-level authorization
   */
  public static AuthorizeRequest forResource(
      String userId,
      String action,
      String resourceType,
      String resourceId,
      String ownerId,
      AuthorizeRequestContext context) {
    return new AuthorizeRequest(userId, action, resourceType, resourceId, ownerId, context);
  }
}
