package org.budgetanalyzer.service.reactive.api;

import static org.assertj.core.api.Assertions.assertThat;

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
          assertThat(
                  context.getBeanNamesForType(ReactiveErrorWebExceptionHandler.class).length == 1)
              .as(
                  "Should register exactly one ReactiveErrorWebExceptionHandler bean; actual "
                      + "ErrorWebExceptionHandler beans: "
                      + Arrays.toString(errorWebExceptionHandlerBeans))
              .isTrue();
          assertThat(context.getBean(ReactiveErrorWebExceptionHandler.class))
              .as("Should expose ReactiveErrorWebExceptionHandler bean")
              .isNotNull();
          assertThat(context.getBeansOfType(ErrorWebExceptionHandler.class).size())
              .as("Should register exactly one ErrorWebExceptionHandler")
              .isEqualTo(1);
        });
  }

  @Test
  @DisplayName("Should not register reactive fallback error handler outside reactive web apps")
  void shouldNotRegisterReactiveFallbackErrorHandlerOutsideReactiveWebApplications() {
    applicationContextRunner.run(
        context ->
            assertThat(
                    context.getBeanNamesForType(ReactiveErrorWebExceptionHandler.class).length > 0)
                .as("Should NOT register ReactiveErrorWebExceptionHandler in non-web context")
                .isFalse());
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
              assertThat(context.containsBean("customErrorWebExceptionHandler"))
                  .as("Should keep the application-defined ErrorWebExceptionHandler")
                  .isTrue();
              assertThat(
                      context.getBeanNamesForType(ReactiveErrorWebExceptionHandler.class).length
                          > 0)
                  .as("Should back off shared reactive fallback handler when custom handler exists")
                  .isFalse();
              assertThat(context.getBeansOfType(ErrorWebExceptionHandler.class).size())
                  .as("Should expose only the custom ErrorWebExceptionHandler")
                  .isEqualTo(1);
              assertThat(context.getBean(ErrorWebExceptionHandler.class))
                  .as("Primary ErrorWebExceptionHandler should be the custom bean")
                  .isSameAs(context.getBean("customErrorWebExceptionHandler"));
            });
  }
}
