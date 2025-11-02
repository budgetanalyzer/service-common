package com.bleurubin.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Utility for safely logging objects with sensitive data masked */
public class SafeLogger {

  private static final Logger log = LoggerFactory.getLogger(SafeLogger.class);

  private static final ObjectMapper SAFE_MAPPER = createSafeMapper();

  private static ObjectMapper createSafeMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new SensitiveDataModule());

    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    return mapper;
  }

  /** Converts object to JSON string with sensitive fields masked */
  public static String toJson(Object object) {
    try {
      return SAFE_MAPPER.writeValueAsString(object);
    } catch (Exception e) {
      log.warn("Failed to serialize object to JSON: {}", e.getMessage(), e);
      return "{}";
    }
  }

  /**
   * Masks a sensitive string value
   *
   * @param value The value to mask
   * @param showLast Number of characters to show at the end (0 = completely mask)
   * @return Masked value
   */
  public static String mask(String value, int showLast) {
    if (value == null || value.isEmpty()) {
      return value;
    }

    if (showLast == 0) {
      return "********";
    }

    if (value.length() <= showLast) {
      return "*".repeat(value.length());
    }

    String masked = "*".repeat(value.length() - showLast);
    String visible = value.substring(value.length() - showLast);
    return masked + visible;
  }

  /** Completely masks a sensitive value */
  public static String mask(String value) {
    return mask(value, 0);
  }
}
