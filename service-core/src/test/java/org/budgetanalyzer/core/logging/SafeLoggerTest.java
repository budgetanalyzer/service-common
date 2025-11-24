package org.budgetanalyzer.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SafeLoggerTest {

  @Test
  void testToJson_withSimpleObject() {
    var data = Map.of("key", "value", "count", 42);
    var json = SafeLogger.toJson(data);

    assertThat(json).contains("\"key\"");
    assertThat(json).contains("\"value\"");
    assertThat(json).contains("\"count\"");
    assertThat(json).contains("42");
  }

  @Test
  void testToJson_withDateTime() {
    var data = Map.of("timestamp", LocalDateTime.of(2024, 1, 15, 10, 30, 0));
    var json = SafeLogger.toJson(data);

    assertThat(json).contains("\"timestamp\"");
    assertThat(json).contains("2024-01-15");
  }

  @Test
  void testToJson_withUnknownType_usesToString() {
    // Simulate an unknown type like HttpMethod that has no serializable properties
    var unknownType = new EmptyBeanType("GET");
    var data = new HashMap<String, Object>();
    data.put("method", unknownType);
    data.put("uri", "/api/test");

    var json = SafeLogger.toJson(data);

    // Should not fail and should use toString() for the unknown type
    assertThat(json).contains("\"method\"");
    assertThat(json).contains("GET"); // From toString()
    assertThat(json).contains("\"uri\"");
    assertThat(json).contains("/api/test");
  }

  @Test
  void testToJson_withUnknownTypeDirectly_usesToString() {
    var unknownType = new EmptyBeanType("POST");
    var json = SafeLogger.toJson(unknownType);

    assertThat(json).contains("POST");
  }

  @Test
  void testToJson_withNestedUnknownType() {
    var unknownType = new EmptyBeanType("DELETE");
    var nested = Map.of("request", Map.of("method", unknownType, "status", 200));

    var json = SafeLogger.toJson(nested);

    assertThat(json).contains("DELETE");
    assertThat(json).contains("200");
  }

  @Test
  void testToJson_withNullValue() {
    var json = SafeLogger.toJson(null);
    assertThat(json).isEqualTo("null");
  }

  @Test
  void testToJson_withEmptyMap() {
    var json = SafeLogger.toJson(Map.of());
    // Expected: empty JSON object with pretty printing (space between braces)
    var expectedEmptyJson = "{" + " " + "}";
    assertThat(json).isEqualTo(expectedEmptyJson);
  }

  @Test
  void testMask_withShowLast() {
    var masked = SafeLogger.mask("secret123", 3);
    assertThat(masked).isEqualTo("******123");
  }

  @Test
  void testMask_completely() {
    var masked = SafeLogger.mask("secret");
    assertThat(masked).isEqualTo("********");
  }

  @Test
  void testMask_withNull() {
    var masked = SafeLogger.mask(null);
    assertThat(masked).isNull();
  }

  @Test
  void testMask_withEmptyString() {
    var masked = SafeLogger.mask("");
    assertThat(masked).isEmpty();
  }

  @Test
  void testMask_withShortValue() {
    var masked = SafeLogger.mask("ab", 5);
    assertThat(masked).isEqualTo("**");
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
}
