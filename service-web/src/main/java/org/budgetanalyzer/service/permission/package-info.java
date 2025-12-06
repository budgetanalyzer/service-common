/**
 * Permission client for authorization checks against the permission-service.
 *
 * <p>This package provides the {@link org.budgetanalyzer.service.permission.PermissionClient}
 * interface and its HTTP implementation for domain services to authorize user actions.
 *
 * <p>Key classes:
 *
 * <ul>
 *   <li>{@link org.budgetanalyzer.service.permission.PermissionClient} - Main interface for
 *       permission checks
 *   <li>{@link org.budgetanalyzer.service.permission.AuthorizationContext} - Context record for
 *       authorization requests
 *   <li>{@link org.budgetanalyzer.service.permission.HttpPermissionClient} - HTTP implementation
 *       with caching and circuit breaker
 * </ul>
 *
 * @see org.budgetanalyzer.service.permission.PermissionClient
 */
package org.budgetanalyzer.service.permission;
