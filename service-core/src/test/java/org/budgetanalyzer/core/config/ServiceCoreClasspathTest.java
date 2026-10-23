package org.budgetanalyzer.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.AuditorAware;
import org.springframework.util.ClassUtils;

import org.budgetanalyzer.core.csv.CsvParser;
import org.budgetanalyzer.core.logging.HttpBodyLogSanitizer;
import org.budgetanalyzer.core.logging.SafeLogger;
import org.budgetanalyzer.core.logging.Sensitive;

/**
 * Verifies that service-core auto-configuration starts for a non-web consumer without the optional
 * web, JPA, and security technologies on the classpath.
 */
@DisplayName("Service-core classpath without optional technologies")
class ServiceCoreClasspathTest {

  private static final String SERVICE_CORE_PACKAGE_PREFIX = "org.budgetanalyzer.core.";

  private static final FilteredClassLoader OPTIONAL_TECHNOLOGIES_ABSENT =
      new FilteredClassLoader(
          "org.springframework.web",
          "org.springframework.http.server",
          "jakarta.servlet",
          "reactor.netty",
          "org.springframework.data.jpa",
          "org.springframework.orm",
          "jakarta.persistence",
          "org.hibernate.",
          "org.springframework.security");

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withClassLoader(OPTIONAL_TECHNOLOGIES_ABSENT)
          .withConfiguration(AutoConfigurations.of(registeredServiceCoreAutoConfigurations()));

  @Test
  @DisplayName("Should register both service-core auto-configurations for discovery")
  void shouldRegisterServiceCoreAutoConfigurations() {
    assertThat(registeredServiceCoreAutoConfigurations())
        .containsExactlyInAnyOrder(
            ServiceCoreAutoConfiguration.class, ServiceCoreJpaAutoConfiguration.class);
  }

  @Test
  @DisplayName("Should start with web, JPA, and security classes hidden")
  void shouldStartWithOptionalTechnologiesAbsent() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(ServiceCoreAutoConfiguration.class);
          var classLoader = context.getClassLoader();
          assertThat(
                  ClassUtils.isPresent(
                      "org.springframework.web.servlet.DispatcherServlet", classLoader))
              .isFalse();
          assertThat(
                  ClassUtils.isPresent(
                      "org.springframework.web.reactive.DispatcherHandler", classLoader))
              .isFalse();
          assertThat(
                  ClassUtils.isPresent(
                      "org.springframework.data.jpa.repository.JpaRepository", classLoader))
              .isFalse();
          assertThat(ClassUtils.isPresent("jakarta.persistence.Entity", classLoader)).isFalse();
          assertThat(
                  ClassUtils.isPresent(
                      "org.springframework.security.core.context.SecurityContextHolder",
                      classLoader))
              .isFalse();
        });
  }

  @Test
  @DisplayName("Should not activate JPA auditing configuration without JPA")
  void shouldNotActivateJpaAuditingWithoutJpa() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(ServiceCoreJpaAutoConfiguration.class);
          assertThat(context).doesNotHaveBean(AuditorAware.class);
          assertThat(context).doesNotHaveBean("auditorAware");
          assertThat(context).doesNotHaveBean("jpaAuditingHandler");
        });
  }

  @Test
  @DisplayName("Should provide a working CSV parser bean")
  void shouldProvideWorkingCsvParser() {
    contextRunner.run(
        context -> {
          var csvParser = context.getBean(CsvParser.class);
          var csv = "date,amount\n2026-10-23,12.50\n";

          var csvData =
              csvParser.parseCsvInputStream(
                  new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                  "transactions.csv",
                  "bank");

          assertThat(csvData.headers()).containsExactly("date", "amount");
          assertThat(csvData.rows()).hasSize(1);
          assertThat(csvData.rows().getFirst().values())
              .containsExactlyInAnyOrderEntriesOf(Map.of("date", "2026-10-23", "amount", "12.50"));
        });
  }

  @Test
  @DisplayName("Should mask and sanitize log output without optional technologies")
  void shouldMaskAndSanitizeLogOutput() {
    contextRunner.run(
        context -> {
          var json = SafeLogger.toJson(new Credentials("alice", "secret-password-123"));
          var body =
              "{\"password\":\"secret-body-456\",\"name\":\"bob\"}"
                  .getBytes(StandardCharsets.UTF_8);
          var sanitizedBody =
              HttpBodyLogSanitizer.prepareBodyForLogging(
                  body, body.length, "application/json", null, null);

          assertThat(json).contains("\"alice\"").doesNotContain("secret-password-123");
          assertThat(sanitizedBody).contains("\"bob\"").doesNotContain("secret-body-456");
        });
  }

  private static Class<?>[] registeredServiceCoreAutoConfigurations() {
    return ImportCandidates.load(
            AutoConfiguration.class, ServiceCoreClasspathTest.class.getClassLoader())
        .getCandidates()
        .stream()
        .filter(candidate -> candidate.startsWith(SERVICE_CORE_PACKAGE_PREFIX))
        .map(ServiceCoreClasspathTest::loadClass)
        .toArray(Class<?>[]::new);
  }

  private static Class<?> loadClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException exception) {
      throw new IllegalStateException(
          "Registered auto-configuration is missing: " + className, exception);
    }
  }

  private record Credentials(String username, @Sensitive String password) {}
}
