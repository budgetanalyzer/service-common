package org.budgetanalyzer.service.api;

import static org.assertj.core.api.Assertions.assertThat;

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

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getMessage()).isEqualTo(message);
    assertThat(response.getCode()).isNull();
    assertThat(response.getFieldErrors()).isNull();
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

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getMessage()).isEqualTo(message);
    assertThat(response.getCode()).isEqualTo(code);
    assertThat(response.getFieldErrors()).isEqualTo(fieldErrors);
    assertThat(response.getFieldErrors().size()).isEqualTo(2);
  }

  @Test
  @DisplayName("Should build application error with code")
  void shouldBuildApplicationErrorWithCode() {
    var type = ApiErrorType.APPLICATION_ERROR;
    var message = "Business rule violation";
    var code = "NEGATIVE_AMOUNT";

    var response = ApiErrorResponse.builder().type(type).message(message).code(code).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getMessage()).isEqualTo(message);
    assertThat(response.getCode()).isEqualTo(code);
    assertThat(response.getFieldErrors()).isNull();
  }

  @Test
  @DisplayName("Should build validation error with field errors")
  void shouldBuildValidationErrorWithFieldErrors() {
    var type = ApiErrorType.VALIDATION_ERROR;
    var message = "One or more fields have validation errors";
    var fieldErrors = List.of(FieldError.of("amount", "must be positive", "-100.50"));

    var response =
        ApiErrorResponse.builder().type(type).message(message).fieldErrors(fieldErrors).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getMessage()).isEqualTo(message);
    assertThat(response.getFieldErrors()).isNotNull();
    assertThat(response.getFieldErrors().size()).isEqualTo(1);
  }

  @Test
  @DisplayName("Should build not found error")
  void shouldBuildNotFoundError() {
    var type = ApiErrorType.NOT_FOUND;
    var message = "Transaction not found with id: 123";

    var response = ApiErrorResponse.builder().type(type).message(message).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getMessage()).isEqualTo(message);
  }

  @Test
  @DisplayName("Should build service unavailable error")
  void shouldBuildServiceUnavailableError() {
    var type = ApiErrorType.SERVICE_UNAVAILABLE;
    var message = "Database connection failed";

    var response = ApiErrorResponse.builder().type(type).message(message).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getMessage()).isEqualTo(message);
  }

  @Test
  @DisplayName("Should build internal error")
  void shouldBuildInternalError() {
    var type = ApiErrorType.INTERNAL_ERROR;
    var message = "Unexpected server error";

    var response = ApiErrorResponse.builder().type(type).message(message).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getMessage()).isEqualTo(message);
  }

  @Test
  @DisplayName("Should handle null type")
  void shouldHandleNullType() {
    var message = "Error message";

    var response = ApiErrorResponse.builder().type(null).message(message).build();

    assertThat(response.getType()).isNull();
    assertThat(response.getMessage()).isEqualTo(message);
  }

  @Test
  @DisplayName("Should handle null message")
  void shouldHandleNullMessage() {
    var type = ApiErrorType.INTERNAL_ERROR;

    var response = ApiErrorResponse.builder().type(type).message(null).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getMessage()).isNull();
  }

  @Test
  @DisplayName("Should handle null code")
  void shouldHandleNullCode() {
    var type = ApiErrorType.APPLICATION_ERROR;
    var message = "Error";

    var response = ApiErrorResponse.builder().type(type).message(message).code(null).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getMessage()).isEqualTo(message);
    assertThat(response.getCode()).isNull();
  }

  @Test
  @DisplayName("Should handle null field errors")
  void shouldHandleNullFieldErrors() {
    var type = ApiErrorType.VALIDATION_ERROR;
    var message = "Validation failed";

    var response = ApiErrorResponse.builder().type(type).message(message).fieldErrors(null).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getMessage()).isEqualTo(message);
    assertThat(response.getFieldErrors()).isNull();
  }

  @Test
  @DisplayName("Should handle empty field errors list")
  void shouldHandleEmptyFieldErrorsList() {
    var type = ApiErrorType.VALIDATION_ERROR;
    var message = "Validation failed";
    var fieldErrors = List.<FieldError>of();

    var response =
        ApiErrorResponse.builder().type(type).message(message).fieldErrors(fieldErrors).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getFieldErrors()).isNotNull();
    assertThat(response.getFieldErrors()).isEmpty();
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

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.APPLICATION_ERROR);
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

    assertThat(response.getFieldErrors().size()).isEqualTo(3);
  }

  @Test
  @DisplayName("Should allow building minimal error response")
  void shouldAllowBuildingMinimalErrorResponse() {
    var response = ApiErrorResponse.builder().build();

    assertThat(response.getType()).isNull();
    assertThat(response.getMessage()).isNull();
    assertThat(response.getCode()).isNull();
    assertThat(response.getFieldErrors()).isNull();
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

    assertThat(response.getType()).isEqualTo(ApiErrorType.INTERNAL_ERROR);
    assertThat(response.getMessage()).isEqualTo("Second message");
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

    assertThat(response1.getType()).isEqualTo(ApiErrorType.NOT_FOUND);
    assertThat(response2.getType()).isEqualTo(ApiErrorType.INTERNAL_ERROR);
  }
}
