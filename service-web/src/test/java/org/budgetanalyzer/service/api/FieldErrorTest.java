package org.budgetanalyzer.service.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.json.JsonMapper;

/** Unit tests for {@link FieldError}. */
@DisplayName("FieldError Tests")
class FieldErrorTest {

  @Test
  @DisplayName("Should create field error with all parameters")
  void shouldCreateFieldErrorWithAllParameters() {
    var field = "email";
    var message = "must be a valid email address";
    var rejectedValue = "invalid@email";

    var fieldError = FieldError.forField(field, message, rejectedValue);

    assertThat(fieldError.getField()).isEqualTo(field);
    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should create field error with null rejected value")
  void shouldCreateFieldErrorWithNullRejectedValue() {
    var field = "description";
    var message = "must not be null";

    var fieldError = FieldError.forField(field, message, null);

    assertThat(fieldError.getField()).isEqualTo(field);
    assertThat(fieldError.getRejectedValue()).isNull();
  }

  @Test
  @DisplayName("Should reject null field in strict field factory")
  void shouldRejectNullFieldInStrictFieldFactory() {
    assertThatThrownBy(() -> FieldError.forField(null, "validation failed", "some-value"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should reject null message in strict field factory")
  void shouldRejectNullMessageInStrictFieldFactory() {
    assertThatThrownBy(() -> FieldError.forField("amount", null, "-100"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should reject null field in field factory")
  void shouldRejectNullFieldInFieldFactory() {
    assertThatThrownBy(() -> FieldError.of(null, "validation failed", "some-value"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should reject null message in field factory")
  void shouldRejectNullMessageInFieldFactory() {
    assertThatThrownBy(() -> FieldError.of("amount", null, "-100"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should handle string rejected value")
  void shouldHandleStringRejectedValue() {
    var rejectedValue = "invalid-string";

    var fieldError = FieldError.forField("username", "invalid format", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
    assertThat(fieldError.getRejectedValue().getClass()).isEqualTo(String.class);
  }

  @Test
  @DisplayName("Should handle integer rejected value")
  void shouldHandleIntegerRejectedValue() {
    var rejectedValue = -100;

    var fieldError = FieldError.forField("age", "must be positive", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
    assertThat(fieldError.getRejectedValue().getClass()).isEqualTo(Integer.class);
  }

  @Test
  @DisplayName("Should handle long rejected value")
  void shouldHandleLongRejectedValue() {
    var rejectedValue = 999999999999L;

    var fieldError = FieldError.forField("id", "id too large", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
    assertThat(fieldError.getRejectedValue().getClass()).isEqualTo(Long.class);
  }

  @Test
  @DisplayName("Should handle BigDecimal rejected value")
  void shouldHandleBigDecimalRejectedValue() {
    var rejectedValue = new BigDecimal("-100.50");

    var fieldError = FieldError.forField("amount", "must be positive", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
    assertThat(fieldError.getRejectedValue().getClass()).isEqualTo(BigDecimal.class);
  }

  @Test
  @DisplayName("Should handle boolean rejected value")
  void shouldHandleBooleanRejectedValue() {
    var rejectedValue = false;

    var fieldError = FieldError.forField("active", "must be true", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
    assertThat(fieldError.getRejectedValue().getClass()).isEqualTo(Boolean.class);
  }

  @Test
  @DisplayName("Should handle list rejected value")
  void shouldHandleListRejectedValue() {
    var rejectedValue = List.of("a", "b", "c");

    var fieldError = FieldError.forField("tags", "too many items", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should handle map rejected value")
  void shouldHandleMapRejectedValue() {
    var rejectedValue = Map.of("key1", "value1", "key2", "value2");

    var fieldError = FieldError.forField("metadata", "invalid structure", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should handle complex object rejected value")
  void shouldHandleComplexObjectRejectedValue() {
    record User(String name, int age) {}

    var rejectedValue = new User("John", -5);

    var fieldError = FieldError.forField("user", "age must be positive", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should support static factory method pattern")
  void shouldSupportStaticFactoryMethodPattern() {
    var fieldError = FieldError.forField("email", "invalid format", "not-an-email");

    assertThat(fieldError).isNotNull();
    assertThat(fieldError.getField()).isEqualTo("email");
  }

  @Test
  @DisplayName("Should handle nested field names with dot notation")
  void shouldHandleNestedFieldNamesWithDotNotation() {
    var field = "user.address.zipCode";
    var message = "invalid zip code format";
    var rejectedValue = "ABCDE";

    var fieldError = FieldError.forField(field, message, rejectedValue);

    assertThat(fieldError.getField()).isEqualTo(field);
    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should handle array index notation in field names")
  void shouldHandleArrayIndexNotationInFieldNames() {
    var field = "transactions[0].amount";
    var message = "amount must be positive";
    var rejectedValue = new BigDecimal("-50.00");

    var fieldError = FieldError.forField(field, message, rejectedValue);

    assertThat(fieldError.getField()).isEqualTo(field);
  }

  // ==================== Indexed Field Error Tests ====================

  @Test
  @DisplayName("Should create indexed field error with all parameters")
  void shouldCreateIndexedFieldErrorWithAllParameters() {
    var index = 44;
    var field = "amount";
    var message = "must not be null";
    var rejectedValue = "invalid";

    var fieldError = FieldError.forIndexedField(index, field, message, rejectedValue);

    assertThat(fieldError.getIndex()).isEqualTo(index);
    assertThat(fieldError.getField()).isEqualTo(field);
    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should reject null field in strict indexed field factory")
  void shouldRejectNullFieldInStrictIndexedFieldFactory() {
    assertThatThrownBy(() -> FieldError.forIndexedField(0, null, "must not be null", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should reject null message in strict indexed field factory")
  void shouldRejectNullMessageInStrictIndexedFieldFactory() {
    assertThatThrownBy(() -> FieldError.forIndexedField(0, "amount", null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should reject null field in indexed field factory")
  void shouldRejectNullFieldInIndexedFieldFactory() {
    assertThatThrownBy(() -> FieldError.of(0, null, "must not be null", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should reject null message in indexed field factory")
  void shouldRejectNullMessageInIndexedFieldFactory() {
    assertThatThrownBy(() -> FieldError.of(0, "amount", null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should create indexed field error with null rejected value")
  void shouldCreateIndexedFieldErrorWithNullRejectedValue() {
    var index = 0;
    var field = "date";
    var message = "date is required";

    var fieldError = FieldError.forIndexedField(index, field, message, null);

    assertThat(fieldError.getIndex()).isEqualTo(index);
    assertThat(fieldError.getField()).isEqualTo(field);
    assertThat(fieldError.getRejectedValue()).isNull();
  }

  @Test
  @DisplayName("Should create indexed field error with zero index")
  void shouldCreateIndexedFieldErrorWithZeroIndex() {
    var fieldError = FieldError.forIndexedField(0, "field", "error", null);

    assertThat(fieldError.getIndex()).isEqualTo(0);
  }

  @Test
  @DisplayName("Non-indexed field error should have null index")
  void nonIndexedFieldErrorShouldHaveNullIndex() {
    var fieldError = FieldError.forField("email", "invalid format", "bad-email");

    assertThat(fieldError.getIndex()).isNull();
  }

  @ParameterizedTest
  @MethodSource("org.budgetanalyzer.service.api.ErrorModelJsonMappers#mappers")
  @DisplayName("Should serialize indexed field error in explicit property order")
  void shouldSerializeIndexedFieldErrorInExplicitPropertyOrder(JsonMapper mapper) {
    var fieldError = FieldError.forIndexedField(44, "amount", "must be positive", "-100");

    var json = mapper.readTree(mapper.writeValueAsString(fieldError));

    assertThat(ErrorModelJsonMappers.propertyNames(json))
        .containsExactly("index", "field", "message", "rejectedValue");
    assertThat(json.path("index").asInt()).isEqualTo(44);
    assertThat(json.path("field").asString()).isEqualTo("amount");
    assertThat(json.path("message").asString()).isEqualTo("must be positive");
    assertThat(json.path("rejectedValue").asString()).isEqualTo("-100");
  }

  @ParameterizedTest
  @MethodSource("org.budgetanalyzer.service.api.ErrorModelJsonMappers#mappers")
  @DisplayName("Should omit null index and rejected value")
  void shouldOmitNullIndexAndRejectedValue(JsonMapper mapper) {
    var fieldError = FieldError.forField("description", "must not be null", null);

    var json = mapper.readTree(mapper.writeValueAsString(fieldError));

    assertThat(ErrorModelJsonMappers.propertyNames(json)).containsExactly("field", "message");
    assertThat(json.path("field").asString()).isEqualTo("description");
    assertThat(json.path("message").asString()).isEqualTo("must not be null");
  }

  @ParameterizedTest
  @MethodSource("org.budgetanalyzer.service.api.ErrorModelJsonMappers#mappers")
  @DisplayName("Should keep empty rejected values")
  void shouldKeepEmptyRejectedValues(JsonMapper mapper) {
    var emptyString =
        mapper.readTree(mapper.writeValueAsString(FieldError.forField("name", "blank", "")));
    var emptyList =
        mapper.readTree(mapper.writeValueAsString(FieldError.forField("tags", "empty", List.of())));

    assertThat(ErrorModelJsonMappers.propertyNames(emptyString))
        .containsExactly("field", "message", "rejectedValue");
    assertThat(emptyString.path("rejectedValue").isString()).isTrue();
    assertThat(emptyString.path("rejectedValue").asString()).isEmpty();
    assertThat(emptyList.path("rejectedValue").isArray()).isTrue();
    assertThat(emptyList.path("rejectedValue").isEmpty()).isTrue();
  }
}
