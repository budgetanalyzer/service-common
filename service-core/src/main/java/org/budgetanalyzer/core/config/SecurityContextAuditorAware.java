package org.budgetanalyzer.core.config;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Implementation of {@link AuditorAware} that extracts the current user from Spring Security
 * context.
 *
 * <p>This class provides the current user ID to Spring Data JPA auditing for populating {@code
 * createdBy} and {@code updatedBy} fields in {@link
 * org.budgetanalyzer.core.domain.AuditableEntity}.
 *
 * <p>The user ID is extracted from {@link Authentication#getName()}, which typically contains the
 * subject claim from the JWT token (Auth0 user ID).
 *
 * <p>Returns an empty Optional when:
 *
 * <ul>
 *   <li>No security context is available
 *   <li>No authentication is present
 *   <li>The authentication is not authenticated
 * </ul>
 *
 * <p>This allows entities to be created without user tracking in system operations (e.g., scheduled
 * tasks, migrations).
 *
 * <p>This class is instantiated as a bean by {@link
 * ServiceCoreJpaAutoConfiguration#auditorAware()}.
 */
public class SecurityContextAuditorAware implements AuditorAware<String> {

  /**
   * Gets the current auditor (user ID) from the security context.
   *
   * @return an Optional containing the user ID if authenticated, or empty if no user context
   */
  @Override
  public Optional<String> getCurrentAuditor() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .filter(Authentication::isAuthenticated)
        .map(Authentication::getName)
        .filter(name -> !"anonymousUser".equals(name));
  }
}
