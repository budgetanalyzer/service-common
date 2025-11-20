package org.budgetanalyzer.service.integration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import org.budgetanalyzer.core.config.SecurityContextAuditorAware;

/**
 * Minimal Spring Boot application for integration testing.
 *
 * <p>This application simulates how a consuming microservice would configure service-common by
 * including the service-common package in component scanning.
 */
@SpringBootApplication(
    scanBasePackages = {
      "org.budgetanalyzer.service",
      "org.budgetanalyzer.core",
      "org.budgetanalyzer.core.integration.fixture",
      "org.budgetanalyzer.service.integration.fixture"
    })
@EntityScan(
    basePackages = {
      "org.budgetanalyzer.core",
      "org.budgetanalyzer.core.integration.fixture",
      "org.budgetanalyzer.service.integration.fixture"
    })
@EnableJpaRepositories(
    basePackages = {
      "org.budgetanalyzer.core",
      "org.budgetanalyzer.core.integration.fixture",
      "org.budgetanalyzer.service.integration.fixture"
    })
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class TestApplication {

  /**
   * Provides the auditor for JPA auditing in tests.
   *
   * @return an AuditorAware that extracts user from security context
   */
  @Bean
  public AuditorAware<String> auditorAware() {
    return new SecurityContextAuditorAware();
  }
}
