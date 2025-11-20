package org.budgetanalyzer.core.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for service-core.
 *
 * <p>Always enables:
 *
 * <ul>
 *   <li>Component scanning for core utilities (logging, CSV parsing)
 * </ul>
 *
 * <p>JPA-specific configuration (entity scanning, auditing) is handled by {@link
 * ServiceCoreJpaAutoConfiguration}, which runs after DataSource autoconfiguration.
 *
 * <p>This auto-configuration is automatically discovered by Spring Boot via
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 */
@AutoConfiguration
@ComponentScan(basePackages = {"org.budgetanalyzer.core.logging", "org.budgetanalyzer.core.csv"})
public class ServiceCoreAutoConfiguration {}
