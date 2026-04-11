package org.budgetanalyzer.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class ApplicationMetricTagPostProcessorTest {

  private static final String APPLICATION_TAG_PROPERTY = "management.metrics.tags.application";

  private ApplicationMetricTagPostProcessor postProcessor;
  private MockEnvironment environment;

  @BeforeEach
  void setUp() {
    postProcessor = new ApplicationMetricTagPostProcessor();
    environment = new MockEnvironment();
  }

  @Test
  void shouldRegisterApplicationTagAsSpringApplicationNamePlaceholder() {
    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    var source = environment.getPropertySources().get("applicationMetricTagDefaults");
    assertThat(source).isNotNull();
    assertThat(source.getProperty(APPLICATION_TAG_PROPERTY))
        .isEqualTo("${spring.application.name}");
  }

  @Test
  void shouldResolvePlaceholderAgainstSpringApplicationName() {
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("application", Map.of("spring.application.name", "demo")));

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    var result = environment.getProperty(APPLICATION_TAG_PROPERTY);
    assertThat(result).isEqualTo("demo");
  }

  @Test
  void shouldNotOverrideExistingApplicationTag() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "application", Map.of(APPLICATION_TAG_PROPERTY, "custom-service")));

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    var result = environment.getProperty(APPLICATION_TAG_PROPERTY);
    assertThat(result).isEqualTo("custom-service");
  }
}
