package org.budgetanalyzer.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    assertEquals(field, fieldError.getField());
    assertEquals(message, fieldError.getMessage());
    assertEquals(rejectedValue, fieldError.getRejectedValue());
  }

  @Test
  @DisplayName("Should create field error with null rejected value")
  void shouldCreateFieldErrorWithNullRejectedValue() {
    var field = "description";
    var message = "must not be null";

    var fieldError = FieldError.of(field, message, null);

    assertEquals(field, fieldError.getField());
    assertEquals(message, fieldError.getMessage());
    assertNull(fieldError.getRejectedValue());
  }

  @Test
  @DisplayName("Should create field error with null field")
  void shouldCreateFieldErrorWithNullField() {
    var message = "validation failed";
    var rejectedValue = "some-value";

    var fieldError = FieldError.of(null, message, rejectedValue);

    assertNull(fieldError.getField());
    assertEquals(message, fieldError.getMessage());
    assertEquals(rejectedValue, fieldError.getRejectedValue());
  }

  @Test
  @DisplayName("Should create field error with null message")
  void shouldCreateFieldErrorWithNullMessage() {
    var field = "amount";
    var rejectedValue = "-100";

    var fieldError = FieldError.of(field, null, rejectedValue);

    assertEquals(field, fieldError.getField());
    assertNull(fieldError.getMessage());
    assertEquals(rejectedValue, fieldError.getRejectedValue());
  }

  @Test
  @DisplayName("Should create field error with all null values")
  void shouldCreateFieldErrorWithAllNullValues() {
    var fieldError = FieldError.of(null, null, null);

    assertNull(fieldError.getField());
    assertNull(fieldError.getMessage());
    assertNull(fieldError.getRejectedValue());
  }

  @Test
  @DisplayName("Should handle string rejected value")
  void shouldHandleStringRejectedValue() {
    var rejectedValue = "invalid-string";

    var fieldError = FieldError.of("username", "invalid format", rejectedValue);

    assertEquals(rejectedValue, fieldError.getRejectedValue());
    assertEquals(String.class, fieldError.getRejectedValue().getClass());
  }

  @Test
  @DisplayName("Should handle integer rejected value")
  void shouldHandleIntegerRejectedValue() {
    var rejectedValue = -100;

    var fieldError = FieldError.of("age", "must be positive", rejectedValue);

    assertEquals(rejectedValue, fieldError.getRejectedValue());
    assertEquals(Integer.class, fieldError.getRejectedValue().getClass());
  }

  @Test
  @DisplayName("Should handle long rejected value")
  void shouldHandleLongRejectedValue() {
    var rejectedValue = 999999999999L;

    var fieldError = FieldError.of("id", "id too large", rejectedValue);

    assertEquals(rejectedValue, fieldError.getRejectedValue());
    assertEquals(Long.class, fieldError.getRejectedValue().getClass());
  }

  @Test
  @DisplayName("Should handle BigDecimal rejected value")
  void shouldHandleBigDecimalRejectedValue() {
    var rejectedValue = new BigDecimal("-100.50");

    var fieldError = FieldError.of("amount", "must be positive", rejectedValue);

    assertEquals(rejectedValue, fieldError.getRejectedValue());
    assertEquals(BigDecimal.class, fieldError.getRejectedValue().getClass());
  }

  @Test
  @DisplayName("Should handle boolean rejected value")
  void shouldHandleBooleanRejectedValue() {
    var rejectedValue = false;

    var fieldError = FieldError.of("active", "must be true", rejectedValue);

    assertEquals(rejectedValue, fieldError.getRejectedValue());
    assertEquals(Boolean.class, fieldError.getRejectedValue().getClass());
  }

  @Test
  @DisplayName("Should handle list rejected value")
  void shouldHandleListRejectedValue() {
    var rejectedValue = List.of("a", "b", "c");

    var fieldError = FieldError.of("tags", "too many items", rejectedValue);

    assertEquals(rejectedValue, fieldError.getRejectedValue());
  }

  @Test
  @DisplayName("Should handle map rejected value")
  void shouldHandleMapRejectedValue() {
    var rejectedValue = Map.of("key1", "value1", "key2", "value2");

    var fieldError = FieldError.of("metadata", "invalid structure", rejectedValue);

    assertEquals(rejectedValue, fieldError.getRejectedValue());
  }

  @Test
  @DisplayName("Should handle complex object rejected value")
  void shouldHandleComplexObjectRejectedValue() {
    record User(String name, int age) {}

    var rejectedValue = new User("John", -5);

    var fieldError = FieldError.of("user", "age must be positive", rejectedValue);

    assertEquals(rejectedValue, fieldError.getRejectedValue());
  }

  @Test
  @DisplayName("Should support static factory method pattern")
  void shouldSupportStaticFactoryMethodPattern() {
    var fieldError = FieldError.of("email", "invalid format", "not-an-email");

    assertNotNull(fieldError);
    assertEquals("email", fieldError.getField());
  }

  @Test
  @DisplayName("Should handle nested field names with dot notation")
  void shouldHandleNestedFieldNamesWithDotNotation() {
    var field = "user.address.zipCode";
    var message = "invalid zip code format";
    var rejectedValue = "ABCDE";

    var fieldError = FieldError.of(field, message, rejectedValue);

    assertEquals(field, fieldError.getField());
    assertEquals(message, fieldError.getMessage());
    assertEquals(rejectedValue, fieldError.getRejectedValue());
  }

  @Test
  @DisplayName("Should handle array index notation in field names")
  void shouldHandleArrayIndexNotationInFieldNames() {
    var field = "transactions[0].amount";
    var message = "amount must be positive";
    var rejectedValue = new BigDecimal("-50.00");

    var fieldError = FieldError.of(field, message, rejectedValue);

    assertEquals(field, fieldError.getField());
  }
}
