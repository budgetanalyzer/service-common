package org.budgetanalyzer.service.servlet.http;

import static org.assertj.core.api.Assertions.assertThat;

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
    assertThat(properties.isEnabled()).as("Default enabled should be false").isFalse();
    assertThat(properties.getLogLevel()).as("Default log level should be DEBUG").isEqualTo("DEBUG");
    assertThat(properties.isIncludeRequestBody())
        .as("Default includeRequestBody should be false")
        .isFalse();
    assertThat(properties.isIncludeResponseBody())
        .as("Default includeResponseBody should be false")
        .isFalse();
    assertThat(properties.isIncludeRequestHeaders())
        .as("Default includeRequestHeaders should be true")
        .isTrue();
    assertThat(properties.isIncludeResponseHeaders())
        .as("Default includeResponseHeaders should be true")
        .isTrue();
    assertThat(properties.isIncludeQueryParams())
        .as("Default includeQueryParams should be false")
        .isFalse();
    assertThat(properties.isIncludeClientIp())
        .as("Default includeClientIp should be true")
        .isTrue();
    assertThat(properties.getMaxBodySize())
        .as("Default maxBodySize should be 10000")
        .isEqualTo(10000);
    assertThat(properties.getExcludePatterns())
        .as("Default excludePatterns should not be null")
        .isNotNull();
    assertThat(properties.getExcludePatterns().size())
        .as("Default excludePatterns should have 3 entries")
        .isEqualTo(3);
    assertThat(properties.getExcludePatterns().contains("/actuator/**"))
        .as("Should contain /actuator/**")
        .isTrue();
    assertThat(properties.getExcludePatterns().contains("/swagger-ui/**"))
        .as("Should contain /swagger-ui/**")
        .isTrue();
    assertThat(properties.getExcludePatterns().contains("/v3/api-docs/**"))
        .as("Should contain /v3/api-docs/**")
        .isTrue();
    assertThat(properties.getIncludePatterns())
        .as("Default includePatterns should not be null")
        .isNotNull();
    assertThat(properties.getIncludePatterns())
        .as("Default includePatterns should be empty")
        .isEmpty();
    assertThat(properties.getSensitiveHeaders())
        .as("Default sensitiveHeaders should not be null")
        .isNotNull();
    assertThat(properties.getSensitiveHeaders().size())
        .as("Default sensitiveHeaders should have 7 entries")
        .isEqualTo(7);
    assertThat(properties.getSensitiveHeaders().contains("Authorization"))
        .as("Should contain Authorization")
        .isTrue();
    assertThat(properties.getSensitiveHeaders().contains("Cookie"))
        .as("Should contain Cookie")
        .isTrue();
    assertThat(properties.isLogErrorsOnly()).as("Default logErrorsOnly should be false").isFalse();
    assertThat(properties.isSkipHealthCheckAgents())
        .as("Default skipHealthCheckAgents should be true")
        .isTrue();
    assertThat(properties.getHealthCheckUserAgentPrefixes())
        .as("Default healthCheckUserAgentPrefixes should not be null")
        .isNotNull();
    assertThat(properties.getHealthCheckUserAgentPrefixes().size())
        .as("Default healthCheckUserAgentPrefixes should have 3 entries")
        .isEqualTo(3);
    assertThat(properties.getHealthCheckUserAgentPrefixes().contains("kube-probe"))
        .as("Should contain kube-probe")
        .isTrue();
    assertThat(properties.getHealthCheckUserAgentPrefixes().contains("ELB-HealthChecker"))
        .as("Should contain ELB-HealthChecker")
        .isTrue();
    assertThat(properties.getHealthCheckUserAgentPrefixes().contains("GoogleHC"))
        .as("Should contain GoogleHC")
        .isTrue();
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
              assertThat(properties.isEnabled()).as("Should bind enabled property").isTrue();
              assertThat(properties.getLogLevel()).as("Should bind log level").isEqualTo("INFO");
              assertThat(properties.isIncludeRequestBody())
                  .as("Should bind includeRequestBody")
                  .isFalse();
              assertThat(properties.isIncludeResponseBody())
                  .as("Should bind includeResponseBody")
                  .isFalse();
              assertThat(properties.isIncludeRequestHeaders())
                  .as("Should bind includeRequestHeaders")
                  .isFalse();
              assertThat(properties.isIncludeResponseHeaders())
                  .as("Should bind includeResponseHeaders")
                  .isFalse();
              assertThat(properties.isIncludeQueryParams())
                  .as("Should bind includeQueryParams")
                  .isFalse();
              assertThat(properties.isIncludeClientIp())
                  .as("Should bind includeClientIp")
                  .isFalse();
              assertThat(properties.getMaxBodySize()).as("Should bind maxBodySize").isEqualTo(5000);
              assertThat(properties.isLogErrorsOnly()).as("Should bind logErrorsOnly").isTrue();
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
              assertThat(properties.getExcludePatterns().size())
                  .as("Should bind 3 exclude patterns")
                  .isEqualTo(3);
              assertThat(properties.getExcludePatterns().contains("/actuator/**"))
                  .as("Should contain /actuator/**")
                  .isTrue();
              assertThat(properties.getExcludePatterns().contains("/swagger-ui/**"))
                  .as("Should contain /swagger-ui/**")
                  .isTrue();
              assertThat(properties.getExcludePatterns().contains("/v3/api-docs/**"))
                  .as("Should contain /v3/api-docs/**")
                  .isTrue();

              assertThat(properties.getIncludePatterns().size())
                  .as("Should bind 2 include patterns")
                  .isEqualTo(2);
              assertThat(properties.getIncludePatterns().contains("/api/**"))
                  .as("Should contain /api/**")
                  .isTrue();
              assertThat(properties.getIncludePatterns().contains("/admin/**"))
                  .as("Should contain /admin/**")
                  .isTrue();

              assertThat(properties.getSensitiveHeaders().size())
                  .as("Should bind 2 sensitive headers")
                  .isEqualTo(2);
              assertThat(properties.getSensitiveHeaders().contains("Authorization"))
                  .as("Should contain Authorization")
                  .isTrue();
              assertThat(properties.getSensitiveHeaders().contains("X-Custom-Token"))
                  .as("Should contain X-Custom-Token")
                  .isTrue();
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
              assertThat(properties.getExcludePatterns())
                  .as("excludePatterns should not be null")
                  .isNotNull();
              assertThat(properties.getIncludePatterns())
                  .as("includePatterns should not be null")
                  .isNotNull();
              assertThat(properties.getSensitiveHeaders())
                  .as("sensitiveHeaders should not be null")
                  .isNotNull();
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
    assertThat(properties.isEnabled()).as("Should update enabled").isTrue();
    assertThat(properties.getLogLevel()).as("Should update log level").isEqualTo("WARN");
    assertThat(properties.isIncludeRequestBody()).as("Should update includeRequestBody").isFalse();
    assertThat(properties.isIncludeResponseBody())
        .as("Should update includeResponseBody")
        .isFalse();
    assertThat(properties.isIncludeRequestHeaders())
        .as("Should update includeRequestHeaders")
        .isFalse();
    assertThat(properties.isIncludeResponseHeaders())
        .as("Should update includeResponseHeaders")
        .isFalse();
    assertThat(properties.isIncludeQueryParams()).as("Should update includeQueryParams").isFalse();
    assertThat(properties.isIncludeClientIp()).as("Should update includeClientIp").isFalse();
    assertThat(properties.getMaxBodySize()).as("Should update maxBodySize").isEqualTo(20000);
    assertThat(properties.isLogErrorsOnly()).as("Should update logErrorsOnly").isTrue();

    assertThat(properties.getExcludePatterns().size())
        .as("Should have 1 exclude pattern")
        .isEqualTo(1);
    assertThat(properties.getExcludePatterns().contains("/health/**"))
        .as("Should contain /health/**")
        .isTrue();

    assertThat(properties.getIncludePatterns().size())
        .as("Should have 1 include pattern")
        .isEqualTo(1);
    assertThat(properties.getIncludePatterns().contains("/api/**"))
        .as("Should contain /api/**")
        .isTrue();

    assertThat(properties.getSensitiveHeaders().size())
        .as("Should have 2 sensitive headers")
        .isEqualTo(2);
    assertThat(properties.getSensitiveHeaders().contains("X-API-Key"))
        .as("Should contain X-API-Key")
        .isTrue();
    assertThat(properties.getSensitiveHeaders().contains("X-Auth-Token"))
        .as("Should contain X-Auth-Token")
        .isTrue();
  }

  @Test
  void shouldClampNegativeMaxBodySizeToZero() {
    // Arrange & Act
    contextRunner
        .withPropertyValues(
            "budgetanalyzer.service.http-logging.enabled=true",
            "budgetanalyzer.service.http-logging.max-body-size=-1")
        .run(
            context -> {
              var properties = context.getBean(HttpLoggingProperties.class);

              // Assert
              assertThat(properties.getMaxBodySize())
                  .as("Should clamp negative max-body-size values to zero")
                  .isEqualTo(0);
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
              assertThat(properties.getMaxBodySize())
                  .as("Should bind zero value (means no body logging)")
                  .isEqualTo(0);
            });
  }

  @Test
  void shouldClampNegativeMaxBodySizeViaSetter() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act
    properties.setMaxBodySize(-25);

    // Assert
    assertThat(properties.getMaxBodySize())
        .as("Setter should clamp negative values to zero")
        .isEqualTo(0);
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
              assertThat(properties.getMaxBodySize())
                  .as("Should bind large value")
                  .isEqualTo(1000000);
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
                assertThat(properties.getLogLevel())
                    .as("Should bind log level: " + level)
                    .isEqualTo(level);
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
              assertThat(properties.getLogLevel())
                  .as("Should bind invalid value (validation should be done in filter)")
                  .isEqualTo("INVALID");
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
              assertThat(properties.getSensitiveHeaders().contains("Authorization"))
                  .as("Should have default Authorization header")
                  .isTrue();
              assertThat(properties.getSensitiveHeaders().contains("Cookie"))
                  .as("Should have default Cookie header")
                  .isTrue();
              assertThat(properties.getSensitiveHeaders().contains("Set-Cookie"))
                  .as("Should have default Set-Cookie header")
                  .isTrue();
              assertThat(properties.getSensitiveHeaders().contains("X-API-Key"))
                  .as("Should have default X-API-Key header")
                  .isTrue();
              assertThat(properties.getSensitiveHeaders().contains("X-Auth-Token"))
                  .as("Should have default X-Auth-Token header")
                  .isTrue();
              assertThat(properties.getSensitiveHeaders().contains("Proxy-Authorization"))
                  .as("Should have default Proxy-Authorization header")
                  .isTrue();
              assertThat(properties.getSensitiveHeaders().contains("WWW-Authenticate"))
                  .as("Should have default WWW-Authenticate header")
                  .isTrue();
            });
  }

  @Test
  void shouldDetectKubernetesHealthCheckAgent() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act & Assert - Various Kubernetes probe user agents
    assertThat(properties.isHealthCheckAgent("kube-probe/1.34"))
        .as("Should detect kube-probe/1.34")
        .isTrue();
    assertThat(properties.isHealthCheckAgent("kube-probe/1.22"))
        .as("Should detect kube-probe/1.22")
        .isTrue();
    assertThat(properties.isHealthCheckAgent("kube-probe/1.12+"))
        .as("Should detect kube-probe with version suffix")
        .isTrue();
  }

  @Test
  void shouldDetectAwsElbHealthCheckAgent() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act & Assert - AWS ELB user agents
    assertThat(properties.isHealthCheckAgent("ELB-HealthChecker/2.0"))
        .as("Should detect ELB-HealthChecker/2.0 (ALB)")
        .isTrue();
    assertThat(properties.isHealthCheckAgent("ELB-HealthChecker/1.0"))
        .as("Should detect ELB-HealthChecker/1.0 (CLB)")
        .isTrue();
  }

  @Test
  void shouldDetectGcpHealthCheckAgent() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act & Assert - GCP health check user agent
    assertThat(properties.isHealthCheckAgent("GoogleHC/1.0"))
        .as("Should detect GoogleHC/1.0")
        .isTrue();
  }

  @Test
  void shouldNotDetectRegularUserAgents() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act & Assert - Regular user agents should not be detected
    assertThat(properties.isHealthCheckAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
        .as("Should not detect Mozilla browser")
        .isFalse();
    assertThat(properties.isHealthCheckAgent("curl/7.64.1")).as("Should not detect curl").isFalse();
    assertThat(properties.isHealthCheckAgent("PostmanRuntime/7.26.8"))
        .as("Should not detect Postman")
        .isFalse();
    assertThat(properties.isHealthCheckAgent("Go-http-client/1.1"))
        .as("Should not detect Go HTTP client")
        .isFalse();
  }

  @Test
  void shouldHandleNullAndEmptyUserAgent() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act & Assert
    assertThat(properties.isHealthCheckAgent(null))
        .as("Should return false for null user agent")
        .isFalse();
    assertThat(properties.isHealthCheckAgent(""))
        .as("Should return false for empty user agent")
        .isFalse();
  }

  @Test
  void shouldRespectSkipHealthCheckAgentsFlag() {
    // Arrange
    var properties = new HttpLoggingProperties();
    properties.setSkipHealthCheckAgents(false);

    // Act & Assert - Should not detect when disabled
    assertThat(properties.isHealthCheckAgent("kube-probe/1.34"))
        .as("Should not detect when skipHealthCheckAgents is false")
        .isFalse();
  }

  @Test
  void shouldMatchCaseInsensitively() {
    // Arrange
    var properties = new HttpLoggingProperties();

    // Act & Assert - Should match regardless of case
    assertThat(properties.isHealthCheckAgent("KUBE-PROBE/1.34"))
        .as("Should match uppercase KUBE-PROBE")
        .isTrue();
    assertThat(properties.isHealthCheckAgent("Kube-Probe/1.34"))
        .as("Should match mixed case Kube-Probe")
        .isTrue();
    assertThat(properties.isHealthCheckAgent("elb-healthchecker/2.0"))
        .as("Should match lowercase elb-healthchecker")
        .isTrue();
    assertThat(properties.isHealthCheckAgent("googlehc/1.0"))
        .as("Should match lowercase googlehc")
        .isTrue();
  }

  @Test
  void shouldAllowCustomHealthCheckPrefixes() {
    // Arrange
    var properties = new HttpLoggingProperties();
    properties.setHealthCheckUserAgentPrefixes(List.of("CustomProbe", "MyHealthCheck"));

    // Act & Assert
    assertThat(properties.isHealthCheckAgent("CustomProbe/1.0"))
        .as("Should detect custom prefix")
        .isTrue();
    assertThat(properties.isHealthCheckAgent("MyHealthCheck-Agent"))
        .as("Should detect custom prefix with suffix")
        .isTrue();
    assertThat(properties.isHealthCheckAgent("kube-probe/1.34"))
        .as("Should not detect removed default")
        .isFalse();
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
              assertThat(properties.isSkipHealthCheckAgents())
                  .as("Should bind skipHealthCheckAgents")
                  .isFalse();
              assertThat(properties.getHealthCheckUserAgentPrefixes().size())
                  .as("Should bind 2 health check prefixes")
                  .isEqualTo(2);
              assertThat(properties.getHealthCheckUserAgentPrefixes().contains("CustomAgent"))
                  .as("Should contain CustomAgent")
                  .isTrue();
              assertThat(properties.getHealthCheckUserAgentPrefixes().contains("AnotherAgent"))
                  .as("Should contain AnotherAgent")
                  .isTrue();
            });
  }

  @Configuration
  @EnableConfigurationProperties(HttpLoggingProperties.class)
  static class TestConfig {}
}
