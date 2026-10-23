package org.budgetanalyzer.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class SafeLoggerTest {

  private static final JsonMapper PARSER = JsonMapper.builder().build();

  @Test
  void shouldSerializeSimpleObjectToJson() {
    var data = Map.of("key", "value", "count", 42);
    var json = SafeLogger.toJson(data);

    assertThat(json).contains("\"key\"");
    assertThat(json).contains("\"value\"");
    assertThat(json).contains("\"count\"");
    assertThat(json).contains("42");
  }

  @Test
  void shouldSerializeDateTimeToJson() {
    var data = Map.of("timestamp", LocalDateTime.of(2024, 1, 15, 10, 30, 0));
    var json = SafeLogger.toJson(data);

    assertThat(json).contains("\"timestamp\"");
    assertThat(json).contains("2024-01-15");
  }

  @Test
  void shouldSerializeDateTimeAsIsoString() throws Exception {
    var data = Map.of("timestamp", LocalDateTime.of(2024, 1, 15, 10, 30, 0));

    var node = PARSER.readTree(SafeLogger.toJson(data));

    assertThat(node.get("timestamp").isString()).isTrue();
    assertThat(node.get("timestamp").asString()).isEqualTo("2024-01-15T10:30:00");
  }

  @Test
  void shouldSerializeUnknownTypeAsEmptyObject() throws Exception {
    var unknownType = new EmptyBeanType("GET");
    var data = new HashMap<String, Object>();
    data.put("method", unknownType);
    data.put("uri", "/api/test");

    var json = SafeLogger.toJson(data);
    var node = PARSER.readTree(json);

    assertThat(node.get("method").isObject()).isTrue();
    assertThat(node.get("method").isEmpty()).isTrue();
    assertThat(node.get("uri").asString()).isEqualTo("/api/test");
    assertThat(json).doesNotContain("GET");
  }

  @Test
  void shouldSerializeUnknownRootTypeAsEmptyObject() throws Exception {
    var unknownType = new EmptyBeanType("POST");

    var json = SafeLogger.toJson(unknownType);
    var node = PARSER.readTree(json);

    assertThat(node.isObject()).isTrue();
    assertThat(node.isEmpty()).isTrue();
    assertThat(json).doesNotContain("POST");
  }

  @Test
  void shouldSerializeNestedUnknownTypeAsEmptyObject() throws Exception {
    var unknownType = new EmptyBeanType("DELETE");
    var nested = Map.of("request", Map.of("method", unknownType, "status", 200));

    var json = SafeLogger.toJson(nested);
    var node = PARSER.readTree(json);

    var request = node.get("request");
    assertThat(request.get("method").isObject()).isTrue();
    assertThat(request.get("method").isEmpty()).isTrue();
    assertThat(request.get("status").asInt()).isEqualTo(200);
    assertThat(json).doesNotContain("DELETE");
  }

  @Test
  void shouldNotInvokeToStringForEmptyBeanHoldingPrivateSecret() throws Exception {
    var bean = new SecretHoldingBean("private-secret-value");

    var json = SafeLogger.toJson(Map.of("holder", bean));
    var node = PARSER.readTree(json);

    assertThat(node.get("holder").isObject()).isTrue();
    assertThat(node.get("holder").isEmpty()).isTrue();
    assertThat(json).doesNotContain("private-secret-value");
    assertThat(bean.wasToStringInvoked()).isFalse();
  }

  @Test
  void shouldWritePropertiesInDeclarationOrder() {
    var json = SafeLogger.toJson(new OrderedConfiguration("z", "a", "m"));

    var zetaIndex = json.indexOf("\"zeta\"");
    var alphaIndex = json.indexOf("\"alpha\"");
    var middleIndex = json.indexOf("\"middle\"");
    assertThat(zetaIndex).isNotNegative();
    assertThat(alphaIndex).isGreaterThan(zetaIndex);
    assertThat(middleIndex).isGreaterThan(alphaIndex);
  }

  @Test
  void shouldIndentOutput() {
    var json = SafeLogger.toJson(new OrderedConfiguration("z", "a", "m"));

    assertThat(json).contains(System.lineSeparator()).contains("  \"zeta\"");
  }

  @Test
  void shouldReturnNullLiteralWhenSerializingNull() {
    var json = SafeLogger.toJson(null);
    assertThat(json).isEqualTo("null");
  }

  @Test
  void shouldReturnEmptyJsonObjectWhenSerializingEmptyMap() {
    var json = SafeLogger.toJson(Map.of());
    // Expected: empty JSON object with pretty printing (space between braces)
    var expectedEmptyJson = "{" + " " + "}";
    assertThat(json).isEqualTo(expectedEmptyJson);
  }

  @Test
  void shouldPreserveTrailingCharactersWhenMaskingWithShowLast() {
    var masked = SafeLogger.mask("secret123", 3);
    assertThat(masked).isEqualTo("******123");
  }

  @Test
  void shouldMaskValueCompletely() {
    var masked = SafeLogger.mask("secret");
    assertThat(masked).isEqualTo("********");
  }

  @Test
  void shouldReturnNullWhenMaskingNull() {
    var masked = SafeLogger.mask(null);
    assertThat(masked).isNull();
  }

  @Test
  void shouldReturnEmptyStringWhenMaskingEmptyString() {
    var masked = SafeLogger.mask("");
    assertThat(masked).isEmpty();
  }

  @Test
  void shouldMaskEntireShortValueWhenShowLastExceedsLength() {
    var masked = SafeLogger.mask("ab", 5);
    assertThat(masked).isEqualTo("**");
  }

  @Test
  void shouldTruncateStandardUuid() {
    var result = SafeLogger.truncateId("550e8400-e29b-41d4-a716-446655440000");
    assertThat(result).isEqualTo("550e8400…");
  }

  @Test
  void shouldReturnNullPlaceholderWhenTruncatingNull() {
    var result = SafeLogger.truncateId(null);
    assertThat(result).isEqualTo("[null]");
  }

  @Test
  void shouldReturnMaskWhenTruncatingEmptyString() {
    var result = SafeLogger.truncateId("");
    assertThat(result).isEqualTo("***");
  }

  @Test
  void shouldReturnMaskWhenTruncatingShortString() {
    var result = SafeLogger.truncateId("abc");
    assertThat(result).isEqualTo("***");
  }

  @Test
  void shouldReturnMaskWhenTruncatingExactLengthString() {
    var result = SafeLogger.truncateId("12345678");
    assertThat(result).isEqualTo("***");
  }

  @Test
  void shouldTruncateIdUsingCustomVisibleCharacterCount() {
    var result = SafeLogger.truncateId("550e8400-e29b-41d4-a716-446655440000", 4);
    assertThat(result).isEqualTo("550e…");
  }

  @Test
  void shouldReturnMaskWhenVisibleCharacterCountIsNegative() {
    var result = SafeLogger.truncateId("550e8400-e29b-41d4-a716-446655440000", -1);
    assertThat(result).isEqualTo("***");
  }

  @Test
  void shouldReturnMaskWhenVisibleCharacterCountIsZero() {
    var result = SafeLogger.truncateId("550e8400-e29b-41d4-a716-446655440000", 0);
    assertThat(result).isEqualTo("***");
  }

  /**
   * Test class that simulates an "empty bean" like Spring's HttpMethod.
   *
   * <p>This class has no public getters/setters, so Jackson cannot serialize it normally.
   */
  private static class EmptyBeanType {
    private final String value;

    EmptyBeanType(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  /** Empty bean whose {@code toString()} would expose a private sensitive field. */
  private static class SecretHoldingBean {
    @Sensitive private final String secret;
    private final AtomicBoolean toStringInvoked = new AtomicBoolean();

    SecretHoldingBean(String secret) {
      this.secret = secret;
    }

    boolean wasToStringInvoked() {
      return toStringInvoked.get();
    }

    @Override
    public String toString() {
      toStringInvoked.set(true);
      return secret;
    }
  }

  /** Configuration-style bean whose declaration order is not alphabetical. */
  static class OrderedConfiguration {
    private final String zeta;
    private final String alpha;
    private final String middle;

    OrderedConfiguration(String zeta, String alpha, String middle) {
      this.zeta = zeta;
      this.alpha = alpha;
      this.middle = middle;
    }

    public String getZeta() {
      return zeta;
    }

    public String getAlpha() {
      return alpha;
    }

    public String getMiddle() {
      return middle;
    }
  }
}
