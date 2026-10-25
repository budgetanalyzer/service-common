package org.budgetanalyzer.service.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Mapper sources for error-model serialization tests. */
final class ErrorModelJsonMappers {

  private ErrorModelJsonMappers() {}

  /**
   * Provides a plain Jackson mapper, Boot's auto-configured mapper, and a Boot mapper whose global
   * inclusion would otherwise write null properties.
   *
   * @return named mapper arguments
   */
  static Stream<Arguments> mappers() {
    return Stream.of(
        Arguments.argumentSet("plain JsonMapper", JsonMapper.builder().build()),
        Arguments.argumentSet("Boot auto-configured JsonMapper", bootJsonMapper()),
        Arguments.argumentSet(
            "Boot JsonMapper with default-property-inclusion=always",
            bootJsonMapper("spring.jackson.default-property-inclusion=always")));
  }

  static List<String> propertyNames(JsonNode jsonNode) {
    return new ArrayList<>(jsonNode.propertyNames());
  }

  private static JsonMapper bootJsonMapper(String... properties) {
    var jsonMapper = new AtomicReference<JsonMapper>();
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withPropertyValues(properties)
        .run(context -> jsonMapper.set(context.getBean(JsonMapper.class)));
    return jsonMapper.get();
  }
}
