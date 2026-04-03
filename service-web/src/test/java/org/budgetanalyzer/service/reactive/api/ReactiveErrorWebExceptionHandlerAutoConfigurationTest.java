package org.budgetanalyzer.service.reactive.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

import org.budgetanalyzer.service.config.ServiceWebAutoConfiguration;

/** Tests reactive fallback error handler auto-configuration behavior. */
@DisplayName("ReactiveErrorWebExceptionHandler Auto-Configuration Tests")
class ReactiveErrorWebExceptionHandlerAutoConfigurationTest {

  private final ReactiveWebApplicationContextRunner reactiveWebApplicationContextRunner =
      new ReactiveWebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ServiceWebAutoConfiguration.class))
          .withBean(ObjectMapper.class, ObjectMapper::new);

  private final ApplicationContextRunner applicationContextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ServiceWebAutoConfiguration.class));

  @Test
  @DisplayName("Should register reactive fallback error handler in reactive applications")
  void shouldRegisterReactiveFallbackErrorHandlerInReactiveApplications() {
    reactiveWebApplicationContextRunner.run(
        context -> {
          var errorWebExceptionHandlerBeans =
              context.getBeanNamesForType(ErrorWebExceptionHandler.class);
          assertTrue(
              context.getBeanNamesForType(ReactiveErrorWebExceptionHandler.class).length == 1,
              "Should register exactly one ReactiveErrorWebExceptionHandler bean; actual "
                  + "ErrorWebExceptionHandler beans: "
                  + Arrays.toString(errorWebExceptionHandlerBeans));
          assertNotNull(
              context.getBean(ReactiveErrorWebExceptionHandler.class),
              "Should expose ReactiveErrorWebExceptionHandler bean");
          assertEquals(
              1,
              context.getBeansOfType(ErrorWebExceptionHandler.class).size(),
              "Should register exactly one ErrorWebExceptionHandler");
        });
  }

  @Test
  @DisplayName("Should not register reactive fallback error handler outside reactive web apps")
  void shouldNotRegisterReactiveFallbackErrorHandlerOutsideReactiveWebApplications() {
    applicationContextRunner.run(
        context ->
            assertFalse(
                context.getBeanNamesForType(ReactiveErrorWebExceptionHandler.class).length > 0,
                "Should NOT register ReactiveErrorWebExceptionHandler in non-web context"));
  }

  @Test
  @DisplayName("Should back off when the application defines a custom ErrorWebExceptionHandler")
  void shouldBackOffWhenApplicationDefinesCustomReactiveErrorHandler() {
    reactiveWebApplicationContextRunner
        .withBean(
            "customErrorWebExceptionHandler",
            ErrorWebExceptionHandler.class,
            () -> (exchange, throwable) -> Mono.error(throwable))
        .run(
            context -> {
              assertTrue(
                  context.containsBean("customErrorWebExceptionHandler"),
                  "Should keep the application-defined ErrorWebExceptionHandler");
              assertFalse(
                  context.getBeanNamesForType(ReactiveErrorWebExceptionHandler.class).length > 0,
                  "Should back off shared reactive fallback handler when custom handler exists");
              assertEquals(
                  1,
                  context.getBeansOfType(ErrorWebExceptionHandler.class).size(),
                  "Should expose only the custom ErrorWebExceptionHandler");
              assertSame(
                  context.getBean("customErrorWebExceptionHandler"),
                  context.getBean(ErrorWebExceptionHandler.class),
                  "Primary ErrorWebExceptionHandler should be the custom bean");
            });
  }
}
