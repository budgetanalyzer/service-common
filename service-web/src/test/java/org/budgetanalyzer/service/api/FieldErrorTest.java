package org.budgetanalyzer.service.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FieldError}. */
@DisplayName("FieldError Tests")
class FieldErrorTest {

  @Test
  @DisplayName("Should create field error with all parameters")
  void shouldCreateFieldErrorWithAllParameters() {
    var field = "email";
    var message = "must be a valid email address";
    var rejectedValue = "invalid@email";

    var fieldError = FieldError.of(field, message, rejectedValue);

    assertThat(fieldError.getField()).isEqualTo(field);
    assertThat(fieldError.getMessage()).isEqualTo(message);
    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should create field error with null rejected value")
  void shouldCreateFieldErrorWithNullRejectedValue() {
    var field = "description";
    var message = "must not be null";

    var fieldError = FieldError.of(field, message, null);

    assertThat(fieldError.getField()).isEqualTo(field);
    assertThat(fieldError.getMessage()).isEqualTo(message);
    assertThat(fieldError.getRejectedValue()).isNull();
  }

  @Test
  @DisplayName("Should create field error with null field")
  void shouldCreateFieldErrorWithNullField() {
    var message = "validation failed";
    var rejectedValue = "some-value";

    var fieldError = FieldError.of(null, message, rejectedValue);

    assertThat(fieldError.getField()).isNull();
    assertThat(fieldError.getMessage()).isEqualTo(message);
    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should create field error with null message")
  void shouldCreateFieldErrorWithNullMessage() {
    var field = "amount";
    var rejectedValue = "-100";

    var fieldError = FieldError.of(field, null, rejectedValue);

    assertThat(fieldError.getField()).isEqualTo(field);
    assertThat(fieldError.getMessage()).isNull();
    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should create field error with all null values")
  void shouldCreateFieldErrorWithAllNullValues() {
    var fieldError = FieldError.of(null, null, null);

    assertThat(fieldError.getField()).isNull();
    assertThat(fieldError.getMessage()).isNull();
    assertThat(fieldError.getRejectedValue()).isNull();
  }

  @Test
  @DisplayName("Should handle string rejected value")
  void shouldHandleStringRejectedValue() {
    var rejectedValue = "invalid-string";

    var fieldError = FieldError.of("username", "invalid format", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
    assertThat(fieldError.getRejectedValue().getClass()).isEqualTo(String.class);
  }

  @Test
  @DisplayName("Should handle integer rejected value")
  void shouldHandleIntegerRejectedValue() {
    var rejectedValue = -100;

    var fieldError = FieldError.of("age", "must be positive", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
    assertThat(fieldError.getRejectedValue().getClass()).isEqualTo(Integer.class);
  }

  @Test
  @DisplayName("Should handle long rejected value")
  void shouldHandleLongRejectedValue() {
    var rejectedValue = 999999999999L;

    var fieldError = FieldError.of("id", "id too large", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
    assertThat(fieldError.getRejectedValue().getClass()).isEqualTo(Long.class);
  }

  @Test
  @DisplayName("Should handle BigDecimal rejected value")
  void shouldHandleBigDecimalRejectedValue() {
    var rejectedValue = new BigDecimal("-100.50");

    var fieldError = FieldError.of("amount", "must be positive", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
    assertThat(fieldError.getRejectedValue().getClass()).isEqualTo(BigDecimal.class);
  }

  @Test
  @DisplayName("Should handle boolean rejected value")
  void shouldHandleBooleanRejectedValue() {
    var rejectedValue = false;

    var fieldError = FieldError.of("active", "must be true", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
    assertThat(fieldError.getRejectedValue().getClass()).isEqualTo(Boolean.class);
  }

  @Test
  @DisplayName("Should handle list rejected value")
  void shouldHandleListRejectedValue() {
    var rejectedValue = List.of("a", "b", "c");

    var fieldError = FieldError.of("tags", "too many items", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should handle map rejected value")
  void shouldHandleMapRejectedValue() {
    var rejectedValue = Map.of("key1", "value1", "key2", "value2");

    var fieldError = FieldError.of("metadata", "invalid structure", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should handle complex object rejected value")
  void shouldHandleComplexObjectRejectedValue() {
    record User(String name, int age) {}

    var rejectedValue = new User("John", -5);

    var fieldError = FieldError.of("user", "age must be positive", rejectedValue);

    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should support static factory method pattern")
  void shouldSupportStaticFactoryMethodPattern() {
    var fieldError = FieldError.of("email", "invalid format", "not-an-email");

    assertThat(fieldError).isNotNull();
    assertThat(fieldError.getField()).isEqualTo("email");
  }

  @Test
  @DisplayName("Should handle nested field names with dot notation")
  void shouldHandleNestedFieldNamesWithDotNotation() {
    var field = "user.address.zipCode";
    var message = "invalid zip code format";
    var rejectedValue = "ABCDE";

    var fieldError = FieldError.of(field, message, rejectedValue);

    assertThat(fieldError.getField()).isEqualTo(field);
    assertThat(fieldError.getMessage()).isEqualTo(message);
    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should handle array index notation in field names")
  void shouldHandleArrayIndexNotationInFieldNames() {
    var field = "transactions[0].amount";
    var message = "amount must be positive";
    var rejectedValue = new BigDecimal("-50.00");

    var fieldError = FieldError.of(field, message, rejectedValue);

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

    var fieldError = FieldError.of(index, field, message, rejectedValue);

    assertThat(fieldError.getIndex()).isEqualTo(index);
    assertThat(fieldError.getField()).isEqualTo(field);
    assertThat(fieldError.getMessage()).isEqualTo(message);
    assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
  }

  @Test
  @DisplayName("Should create indexed field error with null rejected value")
  void shouldCreateIndexedFieldErrorWithNullRejectedValue() {
    var index = 0;
    var field = "date";
    var message = "date is required";

    var fieldError = FieldError.of(index, field, message, null);

    assertThat(fieldError.getIndex()).isEqualTo(index);
    assertThat(fieldError.getField()).isEqualTo(field);
    assertThat(fieldError.getMessage()).isEqualTo(message);
    assertThat(fieldError.getRejectedValue()).isNull();
  }

  @Test
  @DisplayName("Should create indexed field error with zero index")
  void shouldCreateIndexedFieldErrorWithZeroIndex() {
    var fieldError = FieldError.of(0, "field", "error", null);

    assertThat(fieldError.getIndex()).isEqualTo(0);
  }

  @Test
  @DisplayName("Non-indexed field error should have null index")
  void nonIndexedFieldErrorShouldHaveNullIndex() {
    var fieldError = FieldError.of("email", "invalid format", "bad-email");

    assertThat(fieldError.getIndex()).isNull();
  }
}
