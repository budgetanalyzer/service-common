package org.budgetanalyzer.service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

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
                  JacksonAutoConfiguration.class,
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
  @DisplayName(
      "Should coexist with a service-specific SecurityFilterChain that uses a scoped matcher")
  void shouldCoexistWithScopedSecurityFilterChain() {
    servletContextRunner
        .withUserConfiguration(CustomServletSecurityConfig.class)
        .run(
            context -> {
              assertTrue(
                  context.containsBean("customSecurityFilterChain"),
                  "Should keep the application-defined SecurityFilterChain");
              assertTrue(
                  context.containsBean("securityFilterChain"),
                  "Should register the shared claims-header chain alongside the scoped chain");
            });
  }

  @Test
  @DisplayName("Should register the shared chain at BASIC_AUTH_ORDER so service chains run first")
  void shouldRegisterSharedChainAtBasicAuthOrder() {
    servletContextRunner.run(
        context -> {
          var securityFilterChain = context.getBean("securityFilterChain");
          var order =
              org.springframework.core.annotation.AnnotationUtils.findAnnotation(
                  securityFilterChain.getClass(), Order.class);
          // SecurityFilterChain is a proxy — check the bean definition's method annotation instead
          var beanOrder =
              context
                  .getBeanFactory()
                  .findAnnotationOnBean("securityFilterChain", Order.class);
          assertEquals(
              SecurityProperties.BASIC_AUTH_ORDER,
              beanOrder != null ? beanOrder.value() : order != null ? order.value() : null,
              "Shared chain should be ordered at BASIC_AUTH_ORDER");
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

  @Configuration
  static class CustomServletSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain customSecurityFilterChain(HttpSecurity http) throws Exception {
      return http.securityMatcher("/internal/**")
          .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
          .build();
    }
  }
}
