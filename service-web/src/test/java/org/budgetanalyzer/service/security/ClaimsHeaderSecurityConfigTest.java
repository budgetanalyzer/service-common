package org.budgetanalyzer.service.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * Tests that {@link ClaimsHeaderSecurityConfig} conditionally activates only in servlet contexts.
 */
@DisplayName("ClaimsHeaderSecurityConfig Conditional Auto-Configuration Tests")
class ClaimsHeaderSecurityConfigTest {

  private final WebApplicationContextRunner servletContextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  DispatcherServletAutoConfiguration.class,
                  WebMvcAutoConfiguration.class,
                  SecurityAutoConfiguration.class,
                  ClaimsHeaderSecurityConfig.class));

  private final ReactiveWebApplicationContextRunner reactiveContextRunner =
      new ReactiveWebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ClaimsHeaderSecurityConfig.class));

  private final ApplicationContextRunner nonWebContextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ClaimsHeaderSecurityConfig.class));

  @Test
  @DisplayName("Should activate in servlet web application")
  void shouldActivateInServletWebApplication() {
    servletContextRunner.run(
        context -> {
          assertTrue(
              context.containsBean("securityFilterChain"),
              "Should register securityFilterChain bean in servlet context");
        });
  }

  @Test
  @DisplayName("Should not activate in reactive web application")
  void shouldNotActivateInReactiveWebApplication() {
    reactiveContextRunner.run(
        context -> {
          assertFalse(
              context.containsBean("securityFilterChain"),
              "Should NOT register securityFilterChain bean in reactive context");
        });
  }

  @Test
  @DisplayName("Should not activate in non-web application")
  void shouldNotActivateInNonWebApplication() {
    nonWebContextRunner.run(
        context -> {
          assertFalse(
              context.containsBean("securityFilterChain"),
              "Should NOT register securityFilterChain bean in non-web context");
        });
  }
}
