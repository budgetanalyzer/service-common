package com.bleurubin.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ApiErrorType}. */
@DisplayName("ApiErrorType Tests")
class ApiErrorTypeTest {

  @Test
  @DisplayName("Should have INVALID_REQUEST type")
  void shouldHaveInvalidRequestType() {
    var type = ApiErrorType.INVALID_REQUEST;

    assertNotNull(type);
    assertEquals("INVALID_REQUEST", type.name());
  }

  @Test
  @DisplayName("Should have VALIDATION_ERROR type")
  void shouldHaveValidationErrorType() {
    var type = ApiErrorType.VALIDATION_ERROR;

    assertNotNull(type);
    assertEquals("VALIDATION_ERROR", type.name());
  }

  @Test
  @DisplayName("Should have NOT_FOUND type")
  void shouldHaveNotFoundType() {
    var type = ApiErrorType.NOT_FOUND;

    assertNotNull(type);
    assertEquals("NOT_FOUND", type.name());
  }

  @Test
  @DisplayName("Should have APPLICATION_ERROR type")
  void shouldHaveApplicationErrorType() {
    var type = ApiErrorType.APPLICATION_ERROR;

    assertNotNull(type);
    assertEquals("APPLICATION_ERROR", type.name());
  }

  @Test
  @DisplayName("Should have SERVICE_UNAVAILABLE type")
  void shouldHaveServiceUnavailableType() {
    var type = ApiErrorType.SERVICE_UNAVAILABLE;

    assertNotNull(type);
    assertEquals("SERVICE_UNAVAILABLE", type.name());
  }

  @Test
  @DisplayName("Should have INTERNAL_ERROR type")
  void shouldHaveInternalErrorType() {
    var type = ApiErrorType.INTERNAL_ERROR;

    assertNotNull(type);
    assertEquals("INTERNAL_ERROR", type.name());
  }

  @Test
  @DisplayName("Should have exactly 6 error types")
  void shouldHaveExactlySixErrorTypes() {
    var values = ApiErrorType.values();

    assertEquals(6, values.length);
  }

  @Test
  @DisplayName("Should support valueOf conversion")
  void shouldSupportValueOfConversion() {
    var type = ApiErrorType.valueOf("VALIDATION_ERROR");

    assertEquals(ApiErrorType.VALIDATION_ERROR, type);
  }

  @Test
  @DisplayName("Should be serializable to string")
  void shouldBeSerializableToString() {
    var type = ApiErrorType.APPLICATION_ERROR;

    assertEquals("APPLICATION_ERROR", type.toString());
  }
}
