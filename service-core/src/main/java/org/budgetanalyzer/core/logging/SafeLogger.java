package org.budgetanalyzer.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Utility for safely logging objects with sensitive data masked. */
public class SafeLogger {

  private static final Logger log = LoggerFactory.getLogger(SafeLogger.class);

  private static final ObjectMapper SAFE_MAPPER = createSafeMapper();

  private static ObjectMapper createSafeMapper() {
    var objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.registerModule(new SensitiveDataModule());

    // Register fallback serializer for unknown types (e.g., Spring HttpMethod)
    var fallbackModule = new SimpleModule("UnknownTypeFallbackModule");
    fallbackModule.setSerializerModifier(new UnknownTypeSerializer.ToStringFallbackModifier());
    objectMapper.registerModule(fallbackModule);

    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    return objectMapper;
  }

  /**
   * Converts object to JSON string with sensitive fields masked.
   *
   * @param object the object to serialize to JSON
   * @return JSON string representation with sensitive fields masked
   */
  public static String toJson(Object object) {
    try {
      return SAFE_MAPPER.writeValueAsString(object);
    } catch (Exception e) {
      log.warn("Failed to serialize object to JSON: {}", e.getMessage(), e);
      return "{}";
    }
  }

  /**
   * Masks a sensitive string value.
   *
   * @param value The value to mask
   * @param showLast Number of characters to show at the end (0 or negative = completely mask)
   * @return Masked value
   */
  public static String mask(String value, int showLast) {
    if (value == null || value.isEmpty()) {
      return value;
    }

    if (showLast <= 0) {
      return "********";
    }

    if (value.length() <= showLast) {
      return "*".repeat(value.length());
    }

    String masked = "*".repeat(value.length() - showLast);
    String visible = value.substring(value.length() - showLast);
    return masked + visible;
  }

  /**
   * Completely masks a sensitive value.
   *
   * @param value the value to mask
   * @return completely masked value
   */
  public static String mask(String value) {
    return mask(value, 0);
  }
}
