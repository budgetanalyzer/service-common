package org.budgetanalyzer.smoke.servlet;

import jakarta.validation.constraints.NotBlank;

/**
 * Smoke-test create request.
 *
 * @param name required item name
 */
public record CreateSmokeItemRequest(@NotBlank(message = "Name is required") String name) {}
