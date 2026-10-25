package org.budgetanalyzer.service.integration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot application for servlet-based (Spring MVC) integration testing.
 *
 * <p>This application simulates how a consuming servlet-based microservice would use service-common
 * by relying on Spring Boot autoconfiguration to discover and register service-common components.
 * Only test fixture packages are explicitly scanned - service-common components are discovered via
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 *
 * <p>Tests using this application should specify {@code spring.main.web-application-type=servlet}
 * in {@code @SpringBootTest} properties to disambiguate when both servlet and reactive dependencies
 * are on the test classpath.
 */
@SpringBootApplication(
    scanBasePackages = {
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
public class ServletTestApplication {
  // No main method needed - used only for @SpringBootTest
}
