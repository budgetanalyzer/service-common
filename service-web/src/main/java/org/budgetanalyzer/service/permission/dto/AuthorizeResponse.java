package org.budgetanalyzer.service.permission.dto;

/**
 * Response DTO from the permission-service /v1/authorize endpoint.
 *
 * @param granted true if the authorization was granted, false otherwise
 * @param reason explanation of the authorization decision (for debugging/audit)
 */
public record AuthorizeResponse(boolean granted, String reason) {}
