package org.budgetanalyzer.service.permission.dto;

import java.util.Set;

/**
 * Response DTO from the permission-service /v1/authorize/bulk endpoint.
 *
 * @param accessible set of resource IDs the user is permitted to access
 */
public record BulkAuthorizeResponse(Set<String> accessible) {}
