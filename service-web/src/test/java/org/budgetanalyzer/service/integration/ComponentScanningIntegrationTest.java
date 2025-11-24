package org.budgetanalyzer.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import org.budgetanalyzer.service.config.HttpLoggingProperties;
import org.budgetanalyzer.service.security.test.TestSecurityConfig;
import org.budgetanalyzer.service.servlet.api.ServletApiExceptionHandler;
import org.budgetanalyzer.service.servlet.http.CorrelationIdFilter;
import org.budgetanalyzer.service.servlet.http.HttpLoggingConfig;
import org.budgetanalyzer.service.servlet.http.HttpLoggingFilter;

/**
 * Integration test verifying component scanning works correctly.
 *
 * <p>Simulates a consuming service scenario where service-common beans are discovered via
 * auto-configuration and component scanning with scanBasePackages = {"org.budgetanalyzer.service"}.
 */
@SpringBootTest(
    classes = ServletTestApplication.class,
    properties = {
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-issuer.example.com/",
      "AUTH0_AUDIENCE=https://test-api.example.com",
      "spring.main.web-application-type=servlet"
    })
@ImportAutoConfiguration({
  HttpLoggingConfig.class,
  ServletApiExceptionHandler.class,
  TestSecurityConfig.class
})
@DisplayName("Component Scanning Integration Tests")
class ComponentScanningIntegrationTest {

  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("Should discover all service-common beans via component scanning")
  void shouldDiscoverAllServiceCommonBeans() {
    // Verify all expected beans are registered by type (bean names may vary)
    assertThat(context.getBeansOfType(ServletApiExceptionHandler.class)).hasSize(1);
    assertThat(context.getBeansOfType(CorrelationIdFilter.class)).hasSize(1);
    assertThat(context.getBeansOfType(HttpLoggingFilter.class)).hasSize(1);
    assertThat(context.getBeansOfType(HttpLoggingProperties.class)).hasSize(1);
  }

  @Test
  @DisplayName("Should be able to autowire ServletApiExceptionHandler")
  void shouldAutowireServletApiExceptionHandler() {
    var handler = context.getBean(ServletApiExceptionHandler.class);
    assertThat(handler).isNotNull();
  }

  @Test
  @DisplayName("Should be able to autowire CorrelationIdFilter")
  void shouldAutowireCorrelationIdFilter() {
    var correlationIdFilter = context.getBean(CorrelationIdFilter.class);
    assertThat(correlationIdFilter).isNotNull();
  }

  @Test
  @DisplayName("Should be able to autowire HttpLoggingFilter")
  void shouldAutowireHttpLoggingFilter() {
    var httpLoggingFilter = context.getBean(HttpLoggingFilter.class);
    assertThat(httpLoggingFilter).isNotNull();
  }

  @Test
  @DisplayName("Should be able to autowire HttpLoggingProperties")
  void shouldAutowireHttpLoggingProperties() {
    var properties = context.getBean(HttpLoggingProperties.class);
    assertThat(properties).isNotNull();
  }

  @Test
  @DisplayName("Should have correct bean scopes")
  void shouldHaveCorrectBeanScopes() {
    // All service-common beans should be singletons
    var exceptionHandlerName = context.getBeanNamesForType(ServletApiExceptionHandler.class)[0];
    var correlationIdFilterName = context.getBeanNamesForType(CorrelationIdFilter.class)[0];
    var httpLoggingFilterName = context.getBeanNamesForType(HttpLoggingFilter.class)[0];

    assertThat(context.isSingleton(exceptionHandlerName)).isTrue();
    assertThat(context.isSingleton(correlationIdFilterName)).isTrue();
    assertThat(context.isSingleton(httpLoggingFilterName)).isTrue();
  }

  @Test
  @DisplayName("Should not have bean conflicts")
  void shouldNotHaveBeanConflicts() {
    // Verify each bean type has exactly one instance
    var exceptionHandlers = context.getBeansOfType(ServletApiExceptionHandler.class);
    assertThat(exceptionHandlers).hasSize(1);

    var correlationIdFilters = context.getBeansOfType(CorrelationIdFilter.class);
    assertThat(correlationIdFilters).hasSize(1);

    var httpLoggingFilters = context.getBeansOfType(HttpLoggingFilter.class);
    assertThat(httpLoggingFilters).hasSize(1);

    var httpLoggingProperties = context.getBeansOfType(HttpLoggingProperties.class);
    assertThat(httpLoggingProperties).hasSize(1);
  }

  @Test
  @DisplayName("Should properly wire bean dependencies")
  void shouldProperlyWireBeanDependencies() {
    var httpLoggingFilter = context.getBean(HttpLoggingFilter.class);
    assertThat(httpLoggingFilter).isNotNull();

    // HttpLoggingFilter depends on HttpLoggingProperties
    var httpLoggingProperties = context.getBean(HttpLoggingProperties.class);
    assertThat(httpLoggingProperties).isNotNull();

    // Verify the filter was constructed successfully with its dependencies
    assertThat(httpLoggingFilter.toString()).isNotNull();
  }

  @Test
  @DisplayName("Should register beans from both service and core packages")
  void shouldRegisterBeansFromBothPackages() {
    // Beans from org.budgetanalyzer.service package
    assertThat(context.getBeansOfType(ServletApiExceptionHandler.class)).hasSize(1);

    // Test repositories from org.budgetanalyzer.core package
    // Spring Data JPA repositories don't need @Repository annotation
    var repositories = context.getBeansOfType(org.springframework.data.repository.Repository.class);
    // Should find our test repositories (TestAuditableRepository, TestSoftDeletableRepository)
    assertThat(repositories).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  @DisplayName("Should successfully start application context")
  void shouldSuccessfullyStartApplicationContext() {
    assertThat(context).isNotNull();
    assertThat(context.getApplicationName()).isNotNull();
  }

  @Test
  @DisplayName("Should have no missing bean dependencies")
  void shouldHaveNoMissingBeanDependencies() {
    // If we got this far, all beans were successfully created with their dependencies
    var beanDefinitionNames = context.getBeanDefinitionNames();
    assertThat(beanDefinitionNames).isNotEmpty();

    // Verify all service-common beans are present (check by type, not name)
    assertThat(context.getBeansOfType(ServletApiExceptionHandler.class)).isNotEmpty();
    assertThat(context.getBeansOfType(CorrelationIdFilter.class)).isNotEmpty();
    assertThat(context.getBeansOfType(HttpLoggingFilter.class)).isNotEmpty();
  }
}
