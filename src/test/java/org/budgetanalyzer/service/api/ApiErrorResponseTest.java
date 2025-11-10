package org.budgetanalyzer.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ApiErrorResponse}. */
@DisplayName("ApiErrorResponse Tests")
class ApiErrorResponseTest {

  @Test
  @DisplayName("Should build error response with type and message only")
  void shouldBuildErrorResponseWithTypeAndMessageOnly() {
    var type = ApiErrorType.INVALID_REQUEST;
    var message = "Invalid request format";

    var response = ApiErrorResponse.builder().type(type).message(message).build();

    assertEquals(type, response.getType());
    assertEquals(message, response.getMessage());
    assertNull(response.getCode());
    assertNull(response.getFieldErrors());
  }

  @Test
  @DisplayName("Should build error response with all fields")
  void shouldBuildErrorResponseWithAllFields() {
    var type = ApiErrorType.VALIDATION_ERROR;
    var message = "Validation failed";
    var code = "VALIDATION_001";
    var fieldErrors =
        List.of(
            FieldError.of("email", "invalid format", "not-an-email"),
            FieldError.of("age", "must be positive", -5));

    var response =
        ApiErrorResponse.builder()
            .type(type)
            .message(message)
            .code(code)
            .fieldErrors(fieldErrors)
            .build();

    assertEquals(type, response.getType());
    assertEquals(message, response.getMessage());
    assertEquals(code, response.getCode());
    assertEquals(fieldErrors, response.getFieldErrors());
    assertEquals(2, response.getFieldErrors().size());
  }

  @Test
  @DisplayName("Should build application error with code")
  void shouldBuildApplicationErrorWithCode() {
    var type = ApiErrorType.APPLICATION_ERROR;
    var message = "Business rule violation";
    var code = "NEGATIVE_AMOUNT";

    var response = ApiErrorResponse.builder().type(type).message(message).code(code).build();

    assertEquals(type, response.getType());
    assertEquals(message, response.getMessage());
    assertEquals(code, response.getCode());
    assertNull(response.getFieldErrors());
  }

  @Test
  @DisplayName("Should build validation error with field errors")
  void shouldBuildValidationErrorWithFieldErrors() {
    var type = ApiErrorType.VALIDATION_ERROR;
    var message = "One or more fields have validation errors";
    var fieldErrors = List.of(FieldError.of("amount", "must be positive", "-100.50"));

    var response =
        ApiErrorResponse.builder().type(type).message(message).fieldErrors(fieldErrors).build();

    assertEquals(type, response.getType());
    assertEquals(message, response.getMessage());
    assertNotNull(response.getFieldErrors());
    assertEquals(1, response.getFieldErrors().size());
  }

  @Test
  @DisplayName("Should build not found error")
  void shouldBuildNotFoundError() {
    var type = ApiErrorType.NOT_FOUND;
    var message = "Transaction not found with id: 123";

    var response = ApiErrorResponse.builder().type(type).message(message).build();

    assertEquals(type, response.getType());
    assertEquals(message, response.getMessage());
  }

  @Test
  @DisplayName("Should build service unavailable error")
  void shouldBuildServiceUnavailableError() {
    var type = ApiErrorType.SERVICE_UNAVAILABLE;
    var message = "Database connection failed";

    var response = ApiErrorResponse.builder().type(type).message(message).build();

    assertEquals(type, response.getType());
    assertEquals(message, response.getMessage());
  }

  @Test
  @DisplayName("Should build internal error")
  void shouldBuildInternalError() {
    var type = ApiErrorType.INTERNAL_ERROR;
    var message = "Unexpected server error";

    var response = ApiErrorResponse.builder().type(type).message(message).build();

    assertEquals(type, response.getType());
    assertEquals(message, response.getMessage());
  }

  @Test
  @DisplayName("Should handle null type")
  void shouldHandleNullType() {
    var message = "Error message";

    var response = ApiErrorResponse.builder().type(null).message(message).build();

    assertNull(response.getType());
    assertEquals(message, response.getMessage());
  }

  @Test
  @DisplayName("Should handle null message")
  void shouldHandleNullMessage() {
    var type = ApiErrorType.INTERNAL_ERROR;

    var response = ApiErrorResponse.builder().type(type).message(null).build();

    assertEquals(type, response.getType());
    assertNull(response.getMessage());
  }

  @Test
  @DisplayName("Should handle null code")
  void shouldHandleNullCode() {
    var type = ApiErrorType.APPLICATION_ERROR;
    var message = "Error";

    var response = ApiErrorResponse.builder().type(type).message(message).code(null).build();

    assertEquals(type, response.getType());
    assertEquals(message, response.getMessage());
    assertNull(response.getCode());
  }

  @Test
  @DisplayName("Should handle null field errors")
  void shouldHandleNullFieldErrors() {
    var type = ApiErrorType.VALIDATION_ERROR;
    var message = "Validation failed";

    var response = ApiErrorResponse.builder().type(type).message(message).fieldErrors(null).build();

    assertEquals(type, response.getType());
    assertEquals(message, response.getMessage());
    assertNull(response.getFieldErrors());
  }

  @Test
  @DisplayName("Should handle empty field errors list")
  void shouldHandleEmptyFieldErrorsList() {
    var type = ApiErrorType.VALIDATION_ERROR;
    var message = "Validation failed";
    var fieldErrors = List.<FieldError>of();

    var response =
        ApiErrorResponse.builder().type(type).message(message).fieldErrors(fieldErrors).build();

    assertEquals(type, response.getType());
    assertNotNull(response.getFieldErrors());
    assertTrue(response.getFieldErrors().isEmpty());
  }

  @Test
  @DisplayName("Should support method chaining in builder")
  void shouldSupportMethodChainingInBuilder() {
    var response =
        ApiErrorResponse.builder()
            .type(ApiErrorType.APPLICATION_ERROR)
            .message("Business error")
            .code("BIZ_001")
            .build();

    assertNotNull(response);
    assertEquals(ApiErrorType.APPLICATION_ERROR, response.getType());
  }

  @Test
  @DisplayName("Should handle multiple field errors")
  void shouldHandleMultipleFieldErrors() {
    var fieldErrors =
        List.of(
            FieldError.of("email", "invalid format", "bad-email"),
            FieldError.of("age", "must be positive", -1),
            FieldError.of("name", "must not be blank", ""));

    var response =
        ApiErrorResponse.builder()
            .type(ApiErrorType.VALIDATION_ERROR)
            .message("Multiple validation errors")
            .fieldErrors(fieldErrors)
            .build();

    assertEquals(3, response.getFieldErrors().size());
  }

  @Test
  @DisplayName("Should allow building minimal error response")
  void shouldAllowBuildingMinimalErrorResponse() {
    var response = ApiErrorResponse.builder().build();

    assertNull(response.getType());
    assertNull(response.getMessage());
    assertNull(response.getCode());
    assertNull(response.getFieldErrors());
  }

  @Test
  @DisplayName("Should overwrite values when builder methods called multiple times")
  void shouldOverwriteValuesWhenBuilderMethodsCalledMultipleTimes() {
    var response =
        ApiErrorResponse.builder()
            .type(ApiErrorType.NOT_FOUND)
            .type(ApiErrorType.INTERNAL_ERROR) // Overwrite
            .message("First message")
            .message("Second message") // Overwrite
            .build();

    assertEquals(ApiErrorType.INTERNAL_ERROR, response.getType());
    assertEquals("Second message", response.getMessage());
  }

  @Test
  @DisplayName("Should create independent instances from same builder class")
  void shouldCreateIndependentInstancesFromSameBuilderClass() {
    var response1 =
        ApiErrorResponse.builder().type(ApiErrorType.NOT_FOUND).message("Not found").build();

    var response2 =
        ApiErrorResponse.builder()
            .type(ApiErrorType.INTERNAL_ERROR)
            .message("Internal error")
            .build();

    assertEquals(ApiErrorType.NOT_FOUND, response1.getType());
    assertEquals(ApiErrorType.INTERNAL_ERROR, response2.getType());
  }
}
