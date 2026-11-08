package org.budgetanalyzer.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import tools.jackson.databind.json.JsonMapper;

import org.budgetanalyzer.service.audit.AuditLogger;
import org.budgetanalyzer.service.servlet.audit.ServletAuditConfig;

class AuditAutoConfigurationTest {

  @Test
  void shouldExposeAuditLoggerAndServletInterceptorConfigurationInServletApplications() {
    new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ServiceWebAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context.getBeansOfType(AuditLogger.class)).hasSize(1);
              assertThat(context.getBeansOfType(ServletAuditConfig.class)).hasSize(1);
            });
  }

  @Test
  void shouldExposeAuditLoggerWithoutServletConfigurationInReactiveApplications() {
    new ReactiveWebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ServiceWebAutoConfiguration.class))
        .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
        .run(
            context -> {
              assertThat(context.getBeansOfType(AuditLogger.class)).hasSize(1);
              assertThat(context.getBeansOfType(ServletAuditConfig.class)).isEmpty();
            });
  }
}
