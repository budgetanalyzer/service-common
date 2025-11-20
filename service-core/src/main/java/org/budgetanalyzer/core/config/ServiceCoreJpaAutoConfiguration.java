package org.budgetanalyzer.core.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA-specific auto-configuration for service-core.
 *
 * <p>Only loaded if DataSource bean exists (i.e., service added spring-boot-starter-data-jpa
 * dependency and DataSource auto-configuration is enabled).
 *
 * <p>Enables:
 *
 * <ul>
 *   <li>Entity scanning for org.budgetanalyzer.core.domain package
 *   <li>JPA auditing for AuditableEntity timestamps and user tracking
 *   <li>AuditorAware bean for createdBy/updatedBy population from SecurityContext
 * </ul>
 *
 * <p>This auto-configuration runs after {@link DataSourceAutoConfiguration} to ensure the
 * DataSource bean is available when conditions are evaluated.
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, JpaRepository.class})
@EntityScan(basePackages = "org.budgetanalyzer.core.domain")
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class ServiceCoreJpaAutoConfiguration {

  /**
   * Provides the current auditor (user ID) for JPA auditing.
   *
   * <p>Extracts the user ID from Spring Security context for populating {@code createdBy} and
   * {@code updatedBy} fields. Returns empty when no security context is available (e.g., in system
   * operations or tests without authentication).
   *
   * @return an AuditorAware that provides the current user ID
   */
  @Bean
  public AuditorAware<String> auditorAware() {
    return new SecurityContextAuditorAware();
  }
}
