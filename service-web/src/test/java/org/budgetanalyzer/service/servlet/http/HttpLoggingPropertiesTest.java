package org.budgetanalyzer.service.servlet.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import org.budgetanalyzer.service.config.HttpLoggingProperties;

class HttpLoggingPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Test
  void shouldHaveCorrectDefaultValues() {
    // Act
    var properties = new HttpLoggingProperties();

    // Assert
    assertFalse(properties.isEnabled(), "Default enabled should be false");
    assertEquals("DEBUG", properties.getLogLevel(), "Default log level should be DEBUG");
    assertTrue(properties.isIncludeRequestBody(), "Default includeRequestBody should be true");
    assertTrue(properties.isIncludeResponseBody(), "Default includeResponseBody should be true");
    assertTrue(
        properties.isIncludeRequestHeaders(), "Default includeRequestHeaders should be true");
    assertTrue(
        properties.isIncludeResponseHeaders(), "Default includeResponseHeaders should be true");
    assertTrue(properties.isIncludeQueryParams(), "Default includeQueryParams should be true");
    assertTrue(properties.isIncludeClientIp(), "Default includeClientIp should be true");
    assertEquals(10000, properties.getMaxBodySize(), "Default maxBodySize should be 10000");
    assertNotNull(properties.getExcludePatterns(), "Default excludePatterns should not be null");
    assertEquals(
        3, properties.getExcludePatterns().size(), "Default excludePatterns should have 3 entries");
    assertTrue(
        properties.getExcludePatterns().contains("/actuator/**"), "Should contain /actuator/**");
    assertTrue(
        properties.getExcludePatterns().contains("/swagger-ui/**"),
        "Should contain /swagger-ui/**");
    assertTrue(
        properties.getExcludePatterns().contains("/v3/api-docs/**"),
        "Should contain /v3/api-docs/**");
    assertNotNull(properties.getIncludePatterns(), "Default includePatterns should not be null");
    assertTrue(
        properties.getIncludePatterns().isEmpty(), "Default includePatterns should be empty");
    assertNotNull(properties.getSensitiveHeaders(), "Default sensitiveHeaders should not be null");
    assertEquals(
        7,
        properties.getSensitiveHeaders().size(),
        "Default sensitiveHeaders should have 7 entries");
    assertTrue(
        properties.getSensitiveHeaders().contains("Authorization"), "Should contain Authorization");
    assertTrue(properties.getSensitiveHeaders().contains("Cookie"), "Should contain Cookie");
    assertFalse(properties.isLogErrorsOnly(), "Default logErrorsOnly should be false");
    assertTrue(
        properties.isSkipHealthCheckAgents(), "Default skipHealthCheckAgents should be true");
    assertNotNull(
        properties.getHealthCheckUserAgentPrefixes(),
        "Default healthCheckUserAgentPrefixes should not be null");
    assertEquals(
        3,
        properties.getHealthCheckUserAgentPrefixes().size(),
        "Default healthCheckUserAgentPrefixes should have 3 entries");
    assertTrue(
        properties.getHealthCheckUserAgentPrefixes().contains("kube-probe"),
        "Should contain kube-probe");
    assertTrue(
        properties.getHealthCheckUserAgentPrefixes().contains("ELB-HealthChecker"),
        "Should contain ELB-HealthChecker");
    assertTrue(
        properties.getHealthCheckUserAgentPrefixes().contains("GoogleHC"),
        "Should contain GoogleHC");
  }

  @Test
  void shouldBindPropertiesFromConfiguration() {
    // Arrange & Act
    contextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.enabled=true",
            "budgetanalyzer.service.http-logging.log-level=INFO",
            "budgetanalyzer.service.http-logging.include-request-body=false",
            "budgetanalyzer.service.http-logging.include-response-body=false",
            "budgetanalyzer.service.http-logging.include-request-headers=false",
            "budgetanalyzer.service.http-logging.include-response-headers=false",
            "budgetanalyzer.service.http-logging.include-query-params=false",
            "budgetanalyzer.service.http-logging.include-client-ip=false",
            "budgetanalyzer.service.http-logging.max-body-size=5000",
            "budgetanalyzer.service.http-logging.log-errors-only=true")
        .run(
            context -> {
              var properties = context.getBean(HttpLoggingProperties.class);

              // Assert
              assertTrue(properties.isEnabled(), "Should bind enabled property");
              assertEquals("INFO", properties.getLogLevel(), "Should bind log level");
              assertFalse(properties.isIncludeRequestBody(), "Should bind includeRequestBody");
              assertFalse(properties.isIncludeResponseBody(), "Should bind includeResponseBody");
              assertFalse(
                  properties.isIncludeRequestHeaders(), "Should bind includeRequestHeaders");
              assertFalse(
                  properties.isIncludeResponseHeaders(), "Should bind includeResponseHeaders");
              assertFalse(properties.isIncludeQueryParams(), "Should bind includeQueryParams");
              assertFalse(properties.isIncludeClientIp(), "Should bind includeClientIp");
              assertEquals(5000, properties.getMaxBodySize(), "Should bind maxBodySize");
              assertTrue(properties.isLogErrorsOnly(), "Should bind logErrorsOnly");
            });
  }

  @Test
  void shouldBindListPropertiesFromConfiguration() {
    // Arrange & Act
    contextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.exclude-patterns[0]=/actuator/**",
            "budgetanalyzer.service.http-logging.exclude-patterns[1]=/swagger-ui/**",
            "budgetanalyzer.service.http-logging.exclude-patterns[2]=/v3/api-docs/**",
            "budgetanalyzer.service.http-logging.include-patterns[0]=/api/**",
            "budgetanalyzer.service.http-logging.include-patterns[1]=/admin/**",
            "budgetanalyzer.service.http-logging.sensitive-headers[0]=Authorization",
            "budgetanalyzer.service.http-logging.sensitive-headers[1]=X-Custom-Token")
        .run(
            context -> {
              var properties = context.getBean(HttpLoggingProperties.class);

              // Assert
              assertEquals(
                  3, properties.getExcludePatterns().size(), "Should bind 3 exclude patterns");
              assertTrue(
                  properties.getExcludePatterns().contains("/actuator/**"),
                  "Should contain /actuator/**");
              assertTrue(
                  properties.getExcludePatterns().contains("/swagger-ui/**"),
                  "Should contain /swagger-ui/**");
              assertTrue(
                  properties.getExcludePatterns().contains("/v3/api-docs/**"),
                  "Should contain /v3/api-docs/**");

              assertEquals(
                  2, properties.getIncludePatterns().size(), "Should bind 2 include patterns");
              assertTrue(
                  properties.getIncludePatterns().contains("/api/**"), "Should contain /api/**");
              assertTrue(
                  properties.getIncludePatterns().contains("/admin/**"),
                  "Should contain /admin/**");

              assertEquals(
                  2, properties.getSensitiveHeaders().size(), "Should bind 2 sensitive headers");
              assertTrue(
                  properties.getSensitiveHeaders().contains("Authorization"),
                  "Should contain Authorization");
              assertTrue(
                  properties.getSensitiveHeaders().contains("X-Custom-Token"),
                  "Should contain X-Custom-Token");
            });
  }

  @Test
  void shouldHandleEmptyListProperties() {
    // Arrange & Act
    contextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.enabled=true",
            "budgetanalyzer.service.http-logging.exclude-patterns=",
            "budgetanalyzer.service.http-logging.include-patterns=",
            "budgetanalyzer.service.http-logging.sensitive-headers=")
        .run(
            context -> {
              var properties = context.getBean(HttpLoggingProperties.class);

              // Assert
              assertNotNull(properties.getExcludePatterns(), "excludePatterns should not be null");
              assertNotNull(properties.getIncludePatterns(), "includePatterns should not be null");
              assertNotNull(
                  properties.getSensitiveHeaders(), "sensitiveHeaders should not be null");
            });
  }

  @Test
  void shouldAllowSettersToModifyProperties() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act
    properties.setEnabled(true);
    properties.setLogLevel("WARN");
    properties.setIncludeRequestBody(false);
    properties.setIncludeResponseBody(false);
    properties.setIncludeRequestHeaders(false);
    properties.setIncludeResponseHeaders(false);
    properties.setIncludeQueryParams(false);
    properties.setIncludeClientIp(false);
    properties.setMaxBodySize(20000);
    properties.setLogErrorsOnly(true);

    var excludePatterns = new ArrayList<String>();
    excludePatterns.add("/health/**");
    properties.setExcludePatterns(excludePatterns);

    var includePatterns = new ArrayList<String>();
    includePatterns.add("/api/**");
    properties.setIncludePatterns(includePatterns);

    var sensitiveHeaders = List.of("X-API-Key", "X-Auth-Token");
    properties.setSensitiveHeaders(sensitiveHeaders);

    // Assert
    assertTrue(properties.isEnabled(), "Should update enabled");
    assertEquals("WARN", properties.getLogLevel(), "Should update log level");
    assertFalse(properties.isIncludeRequestBody(), "Should update includeRequestBody");
    assertFalse(properties.isIncludeResponseBody(), "Should update includeResponseBody");
    assertFalse(properties.isIncludeRequestHeaders(), "Should update includeRequestHeaders");
    assertFalse(properties.isIncludeResponseHeaders(), "Should update includeResponseHeaders");
    assertFalse(properties.isIncludeQueryParams(), "Should update includeQueryParams");
    assertFalse(properties.isIncludeClientIp(), "Should update includeClientIp");
    assertEquals(20000, properties.getMaxBodySize(), "Should update maxBodySize");
    assertTrue(properties.isLogErrorsOnly(), "Should update logErrorsOnly");

    assertEquals(1, properties.getExcludePatterns().size(), "Should have 1 exclude pattern");
    assertTrue(properties.getExcludePatterns().contains("/health/**"), "Should contain /health/**");

    assertEquals(1, properties.getIncludePatterns().size(), "Should have 1 include pattern");
    assertTrue(properties.getIncludePatterns().contains("/api/**"), "Should contain /api/**");

    assertEquals(2, properties.getSensitiveHeaders().size(), "Should have 2 sensitive headers");
    assertTrue(properties.getSensitiveHeaders().contains("X-API-Key"), "Should contain X-API-Key");
    assertTrue(
        properties.getSensitiveHeaders().contains("X-Auth-Token"), "Should contain X-Auth-Token");
  }

  @Test
  void shouldHandleNegativeMaxBodySize() {
    // Arrange & Act
    contextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.enabled=true",
            "budgetanalyzer.service.http-logging.max-body-size=-1")
        .run(
            context -> {
              var properties = context.getBean(HttpLoggingProperties.class);

              // Assert - Spring Boot will bind the value, but it's invalid
              assertEquals(
                  -1,
                  properties.getMaxBodySize(),
                  "Should bind negative value (validation should be done in filter)");
            });
  }

  @Test
  void shouldHandleZeroMaxBodySize() {
    // Arrange & Act
    contextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.enabled=true",
            "budgetanalyzer.service.http-logging.max-body-size=0")
        .run(
            context -> {
              var properties = context.getBean(HttpLoggingProperties.class);

              // Assert
              assertEquals(
                  0, properties.getMaxBodySize(), "Should bind zero value (means no body logging)");
            });
  }

  @Test
  void shouldHandleLargeMaxBodySize() {
    // Arrange & Act
    contextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.enabled=true",
            "budgetanalyzer.service.http-logging.max-body-size=1000000")
        .run(
            context -> {
              var properties = context.getBean(HttpLoggingProperties.class);

              // Assert
              assertEquals(1000000, properties.getMaxBodySize(), "Should bind large value");
            });
  }

  @Test
  void shouldHandleDifferentLogLevels() {
    // Test various log levels
    String[] logLevels = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"};

    for (String level : logLevels) {
      contextRunner
          .withPropertyValues(
              "budgetanalyzer.service.http-logging.enabled=true",
              "budgetanalyzer.service.http-logging.log-level=" + level)
          .run(
              context -> {
                var properties = context.getBean(HttpLoggingProperties.class);

                // Assert
                assertEquals(level, properties.getLogLevel(), "Should bind log level: " + level);
              });
    }
  }

  @Test
  void shouldHandleInvalidLogLevel() {
    // Arrange & Act
    contextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.enabled=true",
            "budgetanalyzer.service.http-logging.log-level=INVALID")
        .run(
            context -> {
              var properties = context.getBean(HttpLoggingProperties.class);

              // Assert - Spring Boot will bind the value, validation should be done elsewhere
              assertEquals(
                  "INVALID",
                  properties.getLogLevel(),
                  "Should bind invalid value (validation should be done in filter)");
            });
  }

  @Test
  void shouldPreserveDefaultSensitiveHeadersWhenNotConfigured() {
    // Arrange & Act
    contextRunner
        .withPropertyValues("budgetanalyzer.service.http-logging.enabled=true")
        .run(
            context -> {
              var properties = context.getBean(HttpLoggingProperties.class);

              // Assert - Should have default sensitive headers
              assertTrue(
                  properties.getSensitiveHeaders().contains("Authorization"),
                  "Should have default Authorization header");
              assertTrue(
                  properties.getSensitiveHeaders().contains("Cookie"),
                  "Should have default Cookie header");
              assertTrue(
                  properties.getSensitiveHeaders().contains("Set-Cookie"),
                  "Should have default Set-Cookie header");
              assertTrue(
                  properties.getSensitiveHeaders().contains("X-API-Key"),
                  "Should have default X-API-Key header");
              assertTrue(
                  properties.getSensitiveHeaders().contains("X-Auth-Token"),
                  "Should have default X-Auth-Token header");
              assertTrue(
                  properties.getSensitiveHeaders().contains("Proxy-Authorization"),
                  "Should have default Proxy-Authorization header");
              assertTrue(
                  properties.getSensitiveHeaders().contains("WWW-Authenticate"),
                  "Should have default WWW-Authenticate header");
            });
  }

  @Test
  void shouldDetectKubernetesHealthCheckAgent() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act & Assert - Various Kubernetes probe user agents
    assertTrue(properties.isHealthCheckAgent("kube-probe/1.34"), "Should detect kube-probe/1.34");
    assertTrue(properties.isHealthCheckAgent("kube-probe/1.22"), "Should detect kube-probe/1.22");
    assertTrue(
        properties.isHealthCheckAgent("kube-probe/1.12+"),
        "Should detect kube-probe with version suffix");
  }

  @Test
  void shouldDetectAwsElbHealthCheckAgent() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act & Assert - AWS ELB user agents
    assertTrue(
        properties.isHealthCheckAgent("ELB-HealthChecker/2.0"),
        "Should detect ELB-HealthChecker/2.0 (ALB)");
    assertTrue(
        properties.isHealthCheckAgent("ELB-HealthChecker/1.0"),
        "Should detect ELB-HealthChecker/1.0 (CLB)");
  }

  @Test
  void shouldDetectGcpHealthCheckAgent() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act & Assert - GCP health check user agent
    assertTrue(properties.isHealthCheckAgent("GoogleHC/1.0"), "Should detect GoogleHC/1.0");
  }

  @Test
  void shouldNotDetectRegularUserAgents() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act & Assert - Regular user agents should not be detected
    assertFalse(
        properties.isHealthCheckAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"),
        "Should not detect Mozilla browser");
    assertFalse(properties.isHealthCheckAgent("curl/7.64.1"), "Should not detect curl");
    assertFalse(
        properties.isHealthCheckAgent("PostmanRuntime/7.26.8"), "Should not detect Postman");
    assertFalse(
        properties.isHealthCheckAgent("Go-http-client/1.1"), "Should not detect Go HTTP client");
  }

  @Test
  void shouldHandleNullAndEmptyUserAgent() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act & Assert
    assertFalse(properties.isHealthCheckAgent(null), "Should return false for null user agent");
    assertFalse(properties.isHealthCheckAgent(""), "Should return false for empty user agent");
  }

  @Test
  void shouldRespectSkipHealthCheckAgentsFlag() {
    // Arrange
    var properties = new HttpLoggingProperties();
    properties.setSkipHealthCheckAgents(false);

    // Act & Assert - Should not detect when disabled
    assertFalse(
        properties.isHealthCheckAgent("kube-probe/1.34"),
        "Should not detect when skipHealthCheckAgents is false");
  }

  @Test
  void shouldMatchCaseInsensitively() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act & Assert - Should match regardless of case
    assertTrue(
        properties.isHealthCheckAgent("KUBE-PROBE/1.34"), "Should match uppercase KUBE-PROBE");
    assertTrue(
        properties.isHealthCheckAgent("Kube-Probe/1.34"), "Should match mixed case Kube-Probe");
    assertTrue(
        properties.isHealthCheckAgent("elb-healthchecker/2.0"),
        "Should match lowercase elb-healthchecker");
    assertTrue(properties.isHealthCheckAgent("googlehc/1.0"), "Should match lowercase googlehc");
  }

  @Test
  void shouldAllowCustomHealthCheckPrefixes() {
    // Arrange
    var properties = new HttpLoggingProperties();
    properties.setHealthCheckUserAgentPrefixes(List.of("CustomProbe", "MyHealthCheck"));

    // Act & Assert
    assertTrue(properties.isHealthCheckAgent("CustomProbe/1.0"), "Should detect custom prefix");
    assertTrue(
        properties.isHealthCheckAgent("MyHealthCheck-Agent"),
        "Should detect custom prefix with suffix");
    assertFalse(
        properties.isHealthCheckAgent("kube-probe/1.34"), "Should not detect removed default");
  }

  @Test
  void shouldBindHealthCheckPropertiesFromConfiguration() {
    // Arrange & Act
    contextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.skip-health-check-agents=false",
            "budgetanalyzer.service.http-logging.health-check-user-agent-prefixes[0]=CustomAgent",
            "budgetanalyzer.service.http-logging.health-check-user-agent-prefixes[1]=AnotherAgent")
        .run(
            context -> {
              var properties = context.getBean(HttpLoggingProperties.class);

              // Assert
              assertFalse(
                  properties.isSkipHealthCheckAgents(), "Should bind skipHealthCheckAgents");
              assertEquals(
                  2,
                  properties.getHealthCheckUserAgentPrefixes().size(),
                  "Should bind 2 health check prefixes");
              assertTrue(
                  properties.getHealthCheckUserAgentPrefixes().contains("CustomAgent"),
                  "Should contain CustomAgent");
              assertTrue(
                  properties.getHealthCheckUserAgentPrefixes().contains("AnotherAgent"),
                  "Should contain AnotherAgent");
            });
  }

  @Configuration
  @EnableConfigurationProperties(HttpLoggingProperties.class)
  static class TestConfig {}
}
