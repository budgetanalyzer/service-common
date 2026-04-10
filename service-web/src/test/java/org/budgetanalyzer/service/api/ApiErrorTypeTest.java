package org.budgetanalyzer.service.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ApiErrorType}. */
@DisplayName("ApiErrorType Tests")
class ApiErrorTypeTest {

  @Test
  @DisplayName("Should have INVALID_REQUEST type")
  void shouldHaveInvalidRequestType() {
    var type = ApiErrorType.INVALID_REQUEST;

    assertThat(type).isNotNull();
    assertThat(type.name()).isEqualTo("INVALID_REQUEST");
  }

  @Test
  @DisplayName("Should have VALIDATION_ERROR type")
  void shouldHaveValidationErrorType() {
    var type = ApiErrorType.VALIDATION_ERROR;

    assertThat(type).isNotNull();
    assertThat(type.name()).isEqualTo("VALIDATION_ERROR");
  }

  @Test
  @DisplayName("Should have NOT_FOUND type")
  void shouldHaveNotFoundType() {
    var type = ApiErrorType.NOT_FOUND;

    assertThat(type).isNotNull();
    assertThat(type.name()).isEqualTo("NOT_FOUND");
  }

  @Test
  @DisplayName("Should have APPLICATION_ERROR type")
  void shouldHaveApplicationErrorType() {
    var type = ApiErrorType.APPLICATION_ERROR;

    assertThat(type).isNotNull();
    assertThat(type.name()).isEqualTo("APPLICATION_ERROR");
  }

  @Test
  @DisplayName("Should have SERVICE_UNAVAILABLE type")
  void shouldHaveServiceUnavailableType() {
    var type = ApiErrorType.SERVICE_UNAVAILABLE;

    assertThat(type).isNotNull();
    assertThat(type.name()).isEqualTo("SERVICE_UNAVAILABLE");
  }

  @Test
  @DisplayName("Should have INTERNAL_ERROR type")
  void shouldHaveInternalErrorType() {
    var type = ApiErrorType.INTERNAL_ERROR;

    assertThat(type).isNotNull();
    assertThat(type.name()).isEqualTo("INTERNAL_ERROR");
  }

  @Test
  @DisplayName("Should have UNAUTHORIZED type")
  void shouldHaveUnauthorizedType() {
    var type = ApiErrorType.UNAUTHORIZED;

    assertThat(type).isNotNull();
    assertThat(type.name()).isEqualTo("UNAUTHORIZED");
  }

  @Test
  @DisplayName("Should have FORBIDDEN type")
  void shouldHavePermissionDeniedType() {
    var type = ApiErrorType.FORBIDDEN;

    assertThat(type).isNotNull();
    assertThat(type.name()).isEqualTo("FORBIDDEN");
  }

  @Test
  @DisplayName("Should have exactly 8 error types")
  void shouldHaveExactlyEightErrorTypes() {
    var values = ApiErrorType.values();

    assertThat(values.length).isEqualTo(8);
  }

  @Test
  @DisplayName("Should support valueOf conversion")
  void shouldSupportValueOfConversion() {
    var type = ApiErrorType.valueOf("VALIDATION_ERROR");

    assertThat(type).isEqualTo(ApiErrorType.VALIDATION_ERROR);
  }

  @Test
  @DisplayName("Should be serializable to string")
  void shouldBeSerializableToString() {
    var type = ApiErrorType.APPLICATION_ERROR;

    assertThat(type.toString()).isEqualTo("APPLICATION_ERROR");
  }
}
