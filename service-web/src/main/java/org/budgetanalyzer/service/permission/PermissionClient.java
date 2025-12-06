package org.budgetanalyzer.service.permission;

import java.util.Map;
import java.util.Set;

/**
 * Client interface for checking user permissions against the permission-service.
 *
 * <p>This interface provides methods for domain services to authorize user actions on resources.
 * Implementations handle caching, circuit breakers, and fail-closed behavior.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Service
 * public class TransactionService {
 *     private final PermissionClient permissionClient;
 *
 *     public Transaction getTransaction(String userId, String transactionId) {
 *         Transaction txn = repository.findById(transactionId).orElseThrow();
 *
 *         if (!permissionClient.canAccess(
 *                 AuthorizationContext.of(userId),
 *                 "read", "transaction",
 *                 transactionId, txn.getOwnerId())) {
 *             throw new AccessDeniedException("Access denied");
 *         }
 *
 *         return txn;
 *     }
 * }
 * }</pre>
 *
 * @see AuthorizationContext
 */
public interface PermissionClient {

  /**
   * Checks if the user can perform an action on a resource type (type-level permission).
   *
   * <p>Use this method for operations that don't involve a specific resource instance, such as
   * creating new resources or listing resources before filtering.
   *
   * @param ctx the authorization context containing user identity and request metadata
   * @param action the action to perform (e.g., "create", "read", "write", "delete")
   * @param resourceType the type of resource (e.g., "transaction", "currency")
   * @return true if the user is permitted to perform the action, false otherwise
   */
  boolean canPerform(AuthorizationContext ctx, String action, String resourceType);

  /**
   * Checks if the user can access a specific resource instance.
   *
   * <p>Use this method for operations on specific resources where ownership matters. The
   * permission-service evaluates both the user's role-based permissions and ownership rules.
   *
   * @param ctx the authorization context containing user identity and request metadata
   * @param action the action to perform (e.g., "read", "write", "delete")
   * @param resourceType the type of resource (e.g., "transaction", "currency")
   * @param resourceId the unique identifier of the resource
   * @param ownerId the user ID of the resource owner
   * @return true if the user is permitted to access the resource, false otherwise
   */
  boolean canAccess(
      AuthorizationContext ctx,
      String action,
      String resourceType,
      String resourceId,
      String ownerId);

  /**
   * Filters a set of resources to those the user can access.
   *
   * <p>Use this method for list operations to efficiently filter results in a single batch request.
   * More efficient than calling {@link #canAccess} for each resource individually.
   *
   * @param ctx the authorization context containing user identity and request metadata
   * @param action the action to filter for (e.g., "read", "write", "delete")
   * @param resourceType the type of resources (e.g., "transaction", "currency")
   * @param resourceIdToOwnerId map of resource IDs to their owner IDs
   * @return set of resource IDs the user is permitted to access
   */
  Set<String> filterAccessible(
      AuthorizationContext ctx,
      String action,
      String resourceType,
      Map<String, String> resourceIdToOwnerId);
}
