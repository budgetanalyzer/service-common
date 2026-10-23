package org.budgetanalyzer.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Utility for safely logging objects with sensitive data masked. */
public class SafeLogger {

  private static final Logger log = LoggerFactory.getLogger(SafeLogger.class);

  // Declaration order keeps configuration dumps readable; dates use Jackson's ISO-8601 default.
  private static final JsonMapper SAFE_MAPPER =
      JsonMapper.builder()
          .addModule(new SensitiveMaskingModule())
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .build();

  /**
   * Converts object to JSON string with sensitive fields masked.
   *
   * <p>Types without serializable properties are written as an empty JSON object; their {@code
   * toString()} is never used. Pass an explicitly known-safe string when a type's string form is
   * needed in the log.
   *
   * @param object the object to serialize to JSON; may be null
   * @return JSON string representation with sensitive fields masked, or the JSON literal {@code
   *     null} when the input is null
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
   * @param value the value to mask; may be null
   * @param showLast number of characters to show at the end (0 or negative = completely mask)
   * @return masked value, or null when the input value is null
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
   * @param value the value to mask; may be null
   * @return completely masked value, or null when the input value is null
   */
  public static String mask(String value) {
    return mask(value, 0);
  }

  /**
   * Truncates an identifier for safe logging, showing only a prefix.
   *
   * @param id the identifier to truncate
   * @param showChars number of leading characters to show
   * @return truncated identifier (e.g. {@code 550e8400…}), {@code [null]} for null, or {@code ***}
   *     for empty/short values
   */
  public static String truncateId(String id, int showChars) {
    if (id == null) {
      return "[null]";
    }
    if (showChars <= 0 || id.isEmpty() || id.length() <= showChars) {
      return "***";
    }
    return id.substring(0, showChars) + "…";
  }

  /**
   * Truncates an identifier for safe logging, showing the first 8 characters.
   *
   * @param id the identifier to truncate
   * @return truncated identifier (e.g. {@code 550e8400…}), {@code [null]} for null, or {@code ***}
   *     for empty/short values
   */
  public static String truncateId(String id) {
    return truncateId(id, 8);
  }
}
