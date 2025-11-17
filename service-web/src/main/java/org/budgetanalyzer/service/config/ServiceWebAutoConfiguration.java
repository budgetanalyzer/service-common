package org.budgetanalyzer.service.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for service-web.
 *
 * <p>Only activates when running as a web application (i.e., Spring Boot Web is on classpath).
 *
 * <p>Enables:
 *
 * <ul>
 *   <li>Global exception handlers ({@link
 *       org.budgetanalyzer.service.api.DefaultApiExceptionHandler})
 *   <li>HTTP request/response logging filters ({@link
 *       org.budgetanalyzer.service.http.HttpLoggingConfig})
 *   <li>OpenAPI configuration ({@link org.budgetanalyzer.service.config.BaseOpenApiConfig})
 * </ul>
 *
 * <p>This auto-configuration is automatically discovered by Spring Boot via
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ComponentScan(
    basePackages = {
      "org.budgetanalyzer.service.api",
      "org.budgetanalyzer.service.http",
      "org.budgetanalyzer.service.config"
    })
public class ServiceWebAutoConfiguration {}
