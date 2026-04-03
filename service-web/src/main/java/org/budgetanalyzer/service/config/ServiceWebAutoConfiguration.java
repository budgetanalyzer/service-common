package org.budgetanalyzer.service.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.web.reactive.error.ErrorWebFluxAutoConfiguration;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.budgetanalyzer.service.reactive.api.ReactiveErrorWebExceptionHandler;

/**
 * Auto-configuration for service-web supporting both servlet and reactive stacks.
 *
 * <p>Conditionally registers components based on which web stack is on the classpath:
 *
 * <ul>
 *   <li>Servlet stack (Spring MVC) - registers servlet-specific beans
 *   <li>Reactive stack (Spring WebFlux) - registers reactive-specific beans
 * </ul>
 *
 * <p>This auto-configuration is automatically discovered by Spring Boot via
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 */
@AutoConfiguration(before = ErrorWebFluxAutoConfiguration.class)
public class ServiceWebAutoConfiguration {

  /**
   * Registers the shared reactive fallback error handler before Boot's default handler.
   *
   * @param objectMapper object mapper used to serialize API error responses
   * @return shared reactive fallback error handler
   */
  @Bean
  @ConditionalOnWebApplication(type = Type.REACTIVE)
  @ConditionalOnClass(name = "org.springframework.web.server.WebFilter")
  @ConditionalOnMissingBean(ErrorWebExceptionHandler.class)
  public ReactiveErrorWebExceptionHandler errorWebExceptionHandler(ObjectMapper objectMapper) {
    return new ReactiveErrorWebExceptionHandler(objectMapper);
  }

  /**
   * Configuration for servlet-based (Spring MVC) web applications.
   *
   * <p>Activates when:
   *
   * <ul>
   *   <li>Application type is SERVLET
   *   <li>jakarta.servlet.Filter is on classpath
   * </ul>
   *
   * <p>Enables:
   *
   * <ul>
   *   <li>Servlet exception handler ({@link
   *       org.budgetanalyzer.service.servlet.api.ServletApiExceptionHandler})
   *   <li>Servlet HTTP logging filters ({@link
   *       org.budgetanalyzer.service.servlet.http.HttpLoggingConfig})
   * </ul>
   */
  @Configuration
  @ConditionalOnWebApplication(type = Type.SERVLET)
  @ConditionalOnClass(name = "jakarta.servlet.Filter")
  @ComponentScan(
      basePackages = {
        "org.budgetanalyzer.service.servlet.http",
        "org.budgetanalyzer.service.servlet.api"
      })
  static class ServletWebConfiguration {
    // Servlet-specific beans registered via component scanning
  }

  /**
   * Configuration for reactive (Spring WebFlux) web applications.
   *
   * <p>Activates when:
   *
   * <ul>
   *   <li>Application type is REACTIVE
   *   <li>org.springframework.web.server.WebFilter is on classpath
   * </ul>
   *
   * <p>Enables:
   *
   * <ul>
   *   <li>Reactive exception handler ({@link
   *       org.budgetanalyzer.service.reactive.api.ReactiveApiExceptionHandler})
   *   <li>Reactive HTTP logging filters ({@link
   *       org.budgetanalyzer.service.reactive.http.ReactiveHttpLoggingConfig})
   * </ul>
   */
  @Configuration
  @ConditionalOnWebApplication(type = Type.REACTIVE)
  @ConditionalOnClass(name = "org.springframework.web.server.WebFilter")
  @ComponentScan(
      basePackages = {
        "org.budgetanalyzer.service.reactive.http",
        "org.budgetanalyzer.service.reactive.api"
      })
  public static class ReactiveWebConfiguration {
    // Reactive-specific beans registered via component scanning
  }
}
