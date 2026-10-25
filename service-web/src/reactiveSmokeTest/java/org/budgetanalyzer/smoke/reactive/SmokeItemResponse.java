package org.budgetanalyzer.smoke.reactive;

/**
 * Successful smoke-test response with an optional field.
 *
 * @param id item identifier
 * @param name item name
 * @param description optional description, null when absent
 */
public record SmokeItemResponse(String id, String name, String description) {}
