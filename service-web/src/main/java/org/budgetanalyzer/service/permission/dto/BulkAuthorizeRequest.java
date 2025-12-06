package org.budgetanalyzer.service.permission.dto;

import java.util.Map;

/**
 * Request DTO for the permission-service /v1/authorize/bulk endpoint.
 *
 * <p>Used by {@link org.budgetanalyzer.service.permission.HttpPermissionClient} to check which
 * resources a user can access from a batch.
 *
 * @param userId the unique identifier of the user
 * @param action the action to filter for
 * @param resourceType the type of resources
 * @param resources map of resource IDs to their owner IDs
 */
public record BulkAuthorizeRequest(
    String userId, String action, String resourceType, Map<String, String> resources) {}
