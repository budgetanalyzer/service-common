package org.budgetanalyzer.service.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Tests that {@link ReactiveClaimsHeaderSecurityConfig} activates only for reactive applications
 * and backs off its default chain when a custom chain exists.
 */
@DisplayName("ReactiveClaimsHeaderSecurityConfig Conditional Auto-Configuration Tests")
class ReactiveClaimsHeaderSecurityConfigTest {

  private final ReactiveWebApplicationContextRunner reactiveContextRunner =
      new ReactiveWebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class,
                  WebFluxAutoConfiguration.class,
                  ReactiveClaimsHeaderSecurityConfig.class));

  private final ApplicationContextRunner nonWebContextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ReactiveClaimsHeaderSecurityConfig.class));

  @Test
  @DisplayName("Should activate in reactive web application")
  void shouldActivateInReactiveWebApplication() {
    reactiveContextRunner.run(
        context -> {
          assertTrue(
              context.containsBean("securityWebFilterChain"),
              "Should register securityWebFilterChain bean in reactive context");
        });
  }

  @Test
  @DisplayName("Should enable reactive method security with authorization manager support")
  void shouldEnableReactiveMethodSecurityWithAuthorizationManagerSupport() {
    var annotation =
        ReactiveClaimsHeaderSecurityConfig.class.getAnnotation(EnableReactiveMethodSecurity.class);

    assertTrue(annotation != null, "Should enable reactive method security");
    assertTrue(
        annotation.useAuthorizationManager(),
        "Should use authorization-manager-based reactive method security");
  }

  @Test
  @DisplayName("Should back off when the application defines a custom SecurityWebFilterChain")
  void shouldBackOffWhenCustomSecurityWebFilterChainExists() {
    reactiveContextRunner
        .withUserConfiguration(CustomReactiveSecurityConfig.class)
        .run(
            context -> {
              assertTrue(
                  context.containsBean("customSecurityWebFilterChain"),
                  "Should keep the application-defined SecurityWebFilterChain");
              assertFalse(
                  context.containsBean("securityWebFilterChain"),
                  "Should back off shared reactive claims security when a custom chain exists");
            });
  }

  @Test
  @DisplayName("Should not activate in non-web application")
  void shouldNotActivateInNonWebApplication() {
    nonWebContextRunner.run(
        context -> {
          assertFalse(
              context.containsBean("securityWebFilterChain"),
              "Should NOT register securityWebFilterChain bean in non-web context");
        });
  }

  @Configuration
  @EnableWebFluxSecurity
  static class CustomReactiveSecurityConfig {

    @Bean
    SecurityWebFilterChain customSecurityWebFilterChain(ServerHttpSecurity http) {
      return http.authorizeExchange(exchange -> exchange.anyExchange().permitAll()).build();
    }
  }
}
