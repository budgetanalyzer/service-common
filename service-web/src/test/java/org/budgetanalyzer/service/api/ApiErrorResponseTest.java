package org.budgetanalyzer.service.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.json.JsonMapper;

/** Unit tests for {@link ApiErrorResponse}. */
@DisplayName("ApiErrorResponse Tests")
class ApiErrorResponseTest {

  private final JsonMapper jsonMapper = new JsonMapper();

  @Test
  @DisplayName("Should build error response with required fields only")
  void shouldBuildErrorResponseWithRequiredFieldsOnly() {
    var type = ApiErrorType.INVALID_REQUEST;
    var message = "Invalid request format";

    var response = ApiErrorResponse.builder(type, message).build();

    assertThat(response.getType()).isEqualTo(type);
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
            FieldError.forField("email", "invalid format", "not-an-email"),
            FieldError.forField("age", "must be positive", -5));

    var response =
        ApiErrorResponse.builder(type, message).code(code).fieldErrors(fieldErrors).build();

    assertThat(response.getType()).isEqualTo(type);
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

    var response = ApiErrorResponse.builder(type, message).code(code).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getCode()).isEqualTo(code);
    assertThat(response.getFieldErrors()).isNull();
  }

  @Test
  @DisplayName("Should build validation error with field errors")
  void shouldBuildValidationErrorWithFieldErrors() {
    var type = ApiErrorType.VALIDATION_ERROR;
    var message = "One or more fields have validation errors";
    var fieldErrors = List.of(FieldError.forField("amount", "must be positive", "-100.50"));

    var response = ApiErrorResponse.builder(type, message).fieldErrors(fieldErrors).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getFieldErrors()).isNotNull();
    assertThat(response.getFieldErrors().size()).isEqualTo(1);
  }

  @Test
  @DisplayName("Should build not found error")
  void shouldBuildNotFoundError() {
    var type = ApiErrorType.NOT_FOUND;
    var message = "Transaction not found with id: 123";

    var response = ApiErrorResponse.builder(type, message).build();

    assertThat(response.getType()).isEqualTo(type);
  }

  @Test
  @DisplayName("Should build service unavailable error")
  void shouldBuildServiceUnavailableError() {
    var type = ApiErrorType.SERVICE_UNAVAILABLE;
    var message = "Database connection failed";

    var response = ApiErrorResponse.builder(type, message).build();

    assertThat(response.getType()).isEqualTo(type);
  }

  @Test
  @DisplayName("Should build internal error")
  void shouldBuildInternalError() {
    var type = ApiErrorType.INTERNAL_ERROR;
    var message = "Unexpected server error";

    var response = ApiErrorResponse.builder(type, message).build();

    assertThat(response.getType()).isEqualTo(type);
  }

  @Test
  @DisplayName("Should reject null type in strict builder")
  void shouldRejectNullTypeInStrictBuilder() {
    assertThatThrownBy(() -> ApiErrorResponse.builder(null, "Error message"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should reject null message in strict builder")
  void shouldRejectNullMessageInStrictBuilder() {
    assertThatThrownBy(() -> ApiErrorResponse.builder(ApiErrorType.INTERNAL_ERROR, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should reject null type when builder setter clears required type")
  void shouldRejectNullTypeWhenBuilderSetterClearsRequiredType() {
    var builder = ApiErrorResponse.builder(ApiErrorType.INTERNAL_ERROR, "Error message");

    assertThatThrownBy(() -> builder.type(null).build()).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should reject null message when builder setter clears required message")
  void shouldRejectNullMessageWhenBuilderSetterClearsRequiredMessage() {
    var builder = ApiErrorResponse.builder(ApiErrorType.INTERNAL_ERROR, "Error message");

    assertThatThrownBy(() -> builder.message(null).build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should handle null code")
  void shouldHandleNullCode() {
    var type = ApiErrorType.APPLICATION_ERROR;
    var message = "Error";

    var response = ApiErrorResponse.builder(type, message).code(null).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getCode()).isNull();
  }

  @Test
  @DisplayName("Should handle null field errors")
  void shouldHandleNullFieldErrors() {
    var type = ApiErrorType.VALIDATION_ERROR;
    var message = "Validation failed";

    var response = ApiErrorResponse.builder(type, message).fieldErrors(null).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getFieldErrors()).isNull();
  }

  @Test
  @DisplayName("Should handle empty field errors list")
  void shouldHandleEmptyFieldErrorsList() {
    var type = ApiErrorType.VALIDATION_ERROR;
    var message = "Validation failed";
    var fieldErrors = List.<FieldError>of();

    var response = ApiErrorResponse.builder(type, message).fieldErrors(fieldErrors).build();

    assertThat(response.getType()).isEqualTo(type);
    assertThat(response.getFieldErrors()).isNotNull();
    assertThat(response.getFieldErrors()).isEmpty();
  }

  @Test
  @DisplayName("Should support method chaining in builder")
  void shouldSupportMethodChainingInBuilder() {
    var response =
        ApiErrorResponse.builder(ApiErrorType.APPLICATION_ERROR, "Business error")
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
            FieldError.forField("email", "invalid format", "bad-email"),
            FieldError.forField("age", "must be positive", -1),
            FieldError.forField("name", "must not be blank", ""));

    var response =
        ApiErrorResponse.builder(ApiErrorType.VALIDATION_ERROR, "Multiple validation errors")
            .fieldErrors(fieldErrors)
            .build();

    assertThat(response.getFieldErrors().size()).isEqualTo(3);
  }

  @Test
  @DisplayName("Should serialize required fields built with strict API")
  void shouldSerializeRequiredFieldsBuiltWithStrictApi() throws Exception {
    var response =
        ApiErrorResponse.builder(ApiErrorType.VALIDATION_ERROR, "Validation failed")
            .fieldErrors(List.of(FieldError.forField("email", "invalid format", "bad-email")))
            .build();

    var json = jsonMapper.readTree(jsonMapper.writeValueAsString(response));

    assertThat(json.path("type").asString()).isEqualTo("VALIDATION_ERROR");
    assertThat(json.has("message")).isTrue();
    assertThat(json.path("fieldErrors").get(0).path("field").asString()).isEqualTo("email");
    assertThat(json.path("fieldErrors").get(0).has("message")).isTrue();
  }

  @Test
  @DisplayName("Should overwrite type when builder method called multiple times")
  void shouldOverwriteTypeWhenBuilderMethodCalledMultipleTimes() {
    var response =
        ApiErrorResponse.builder(ApiErrorType.NOT_FOUND, "First message")
            .type(ApiErrorType.INTERNAL_ERROR) // Overwrite
            .build();

    assertThat(response.getType()).isEqualTo(ApiErrorType.INTERNAL_ERROR);
  }

  @Test
  @DisplayName("Should create independent instances from same builder class")
  void shouldCreateIndependentInstancesFromSameBuilderClass() {
    var response1 = ApiErrorResponse.builder(ApiErrorType.NOT_FOUND, "Not found").build();

    var response2 = ApiErrorResponse.builder(ApiErrorType.INTERNAL_ERROR, "Internal error").build();

    assertThat(response1.getType()).isEqualTo(ApiErrorType.NOT_FOUND);
    assertThat(response2.getType()).isEqualTo(ApiErrorType.INTERNAL_ERROR);
  }

  @ParameterizedTest
  @MethodSource("org.budgetanalyzer.service.api.ErrorModelJsonMappers#mappers")
  @DisplayName("Should serialize populated optional fields in explicit property order")
  void shouldSerializePopulatedOptionalFieldsInExplicitPropertyOrder(JsonMapper mapper) {
    var response =
        ApiErrorResponse.builder(ApiErrorType.VALIDATION_ERROR, "Validation failed")
            .code("VALIDATION_001")
            .fieldErrors(List.of(FieldError.forIndexedField(3, "email", "invalid", "bad-email")))
            .build();

    var json = mapper.readTree(mapper.writeValueAsString(response));

    assertThat(ErrorModelJsonMappers.propertyNames(json))
        .containsExactly("type", "message", "code", "fieldErrors");
    assertThat(json.path("type").asString()).isEqualTo("VALIDATION_ERROR");
    assertThat(json.path("message").asString()).isEqualTo("Validation failed");
    assertThat(json.path("code").asString()).isEqualTo("VALIDATION_001");
    assertThat(ErrorModelJsonMappers.propertyNames(json.path("fieldErrors").get(0)))
        .containsExactly("index", "field", "message", "rejectedValue");
  }

  @ParameterizedTest
  @MethodSource("org.budgetanalyzer.service.api.ErrorModelJsonMappers#mappers")
  @DisplayName("Should omit null optional fields")
  void shouldOmitNullOptionalFields(JsonMapper mapper) {
    var response = ApiErrorResponse.builder(ApiErrorType.NOT_FOUND, "Missing resource").build();

    var json = mapper.readTree(mapper.writeValueAsString(response));

    assertThat(ErrorModelJsonMappers.propertyNames(json)).containsExactly("type", "message");
    assertThat(json.path("type").asString()).isEqualTo("NOT_FOUND");
    assertThat(json.path("message").asString()).isEqualTo("Missing resource");
  }

  @ParameterizedTest
  @MethodSource("org.budgetanalyzer.service.api.ErrorModelJsonMappers#mappers")
  @DisplayName("Should keep empty field errors list and empty code")
  void shouldKeepEmptyFieldErrorsListAndEmptyCode(JsonMapper mapper) {
    var response =
        ApiErrorResponse.builder(ApiErrorType.VALIDATION_ERROR, "Validation failed")
            .code("")
            .fieldErrors(List.of())
            .build();

    var json = mapper.readTree(mapper.writeValueAsString(response));

    assertThat(ErrorModelJsonMappers.propertyNames(json))
        .containsExactly("type", "message", "code", "fieldErrors");
    assertThat(json.path("code").isString()).isTrue();
    assertThat(json.path("code").asString()).isEmpty();
    assertThat(json.path("fieldErrors").isArray()).isTrue();
    assertThat(json.path("fieldErrors").isEmpty()).isTrue();
  }
}
