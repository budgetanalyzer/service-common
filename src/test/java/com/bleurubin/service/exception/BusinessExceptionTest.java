package com.bleurubin.service.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BusinessException}. */
@DisplayName("BusinessException Tests")
class BusinessExceptionTest {

  @Test
  @DisplayName("Should create exception with message and code")
  void shouldCreateExceptionWithMessageAndCode() {
    var message = "Transaction amount must be positive";
    var code = "NEGATIVE_AMOUNT";

    var exception = new BusinessException(message, code);

    assertEquals(message, exception.getMessage());
    assertEquals(code, exception.getCode());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should create exception with message, code, and cause")
  void shouldCreateExceptionWithMessageCodeAndCause() {
    var message = "Budget limit exceeded";
    var code = "BUDGET_EXCEEDED";
    var cause = new IllegalStateException("Current balance insufficient");

    var exception = new BusinessException(message, code, cause);

    assertEquals(message, exception.getMessage());
    assertEquals(code, exception.getCode());
    assertSame(cause, exception.getCause());
  }

  @Test
  @DisplayName("Should extend ServiceException")
  void shouldExtendServiceException() {
    var exception = new BusinessException("Error", "ERROR_CODE");

    assertTrue(exception instanceof ServiceException);
  }

  @Test
  @DisplayName("Should be instance of RuntimeException")
  void shouldBeRuntimeException() {
    var exception = new BusinessException("Error", "ERROR_CODE");

    assertTrue(exception instanceof RuntimeException);
  }

  @Test
  @DisplayName("Should handle null message")
  void shouldHandleNullMessage() {
    var code = "ERROR_CODE";

    var exception = new BusinessException(null, code);

    assertNull(exception.getMessage());
    assertEquals(code, exception.getCode());
  }

  @Test
  @DisplayName("Should handle null code")
  void shouldHandleNullCode() {
    var message = "Business rule violation";

    var exception = new BusinessException(message, null);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCode());
  }

  @Test
  @DisplayName("Should handle null cause")
  void shouldHandleNullCause() {
    var message = "Business rule violation";
    var code = "RULE_VIOLATION";

    var exception = new BusinessException(message, code, null);

    assertEquals(message, exception.getMessage());
    assertEquals(code, exception.getCode());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should handle all null values")
  void shouldHandleAllNullValues() {
    var exception = new BusinessException(null, null, null);

    assertNull(exception.getMessage());
    assertNull(exception.getCode());
    assertNull(exception.getCause());
  }

  @Test
  @DisplayName("Should preserve error code for negative amount scenario")
  void shouldPreserveErrorCodeForNegativeAmount() {
    var exception = new BusinessException("Amount cannot be negative", "NEGATIVE_AMOUNT");

    assertEquals("NEGATIVE_AMOUNT", exception.getCode());
    assertTrue(exception.getMessage().contains("negative"));
  }

  @Test
  @DisplayName("Should preserve error code for budget exceeded scenario")
  void shouldPreserveErrorCodeForBudgetExceeded() {
    var exception = new BusinessException("Budget limit of $1000 exceeded", "BUDGET_EXCEEDED");

    assertEquals("BUDGET_EXCEEDED", exception.getCode());
    assertTrue(exception.getMessage().contains("Budget"));
  }

  @Test
  @DisplayName("Should preserve error code for duplicate detection scenario")
  void shouldPreserveErrorCodeForDuplicateDetection() {
    var exception =
        new BusinessException("Duplicate transaction detected", "DUPLICATE_TRANSACTION");

    assertEquals("DUPLICATE_TRANSACTION", exception.getCode());
    assertTrue(exception.getMessage().contains("Duplicate"));
  }

  @Test
  @DisplayName("Should handle snake_case error codes")
  void shouldHandleSnakeCaseErrorCodes() {
    var exception = new BusinessException("Invalid state transition", "INVALID_STATE_TRANSITION");

    assertEquals("INVALID_STATE_TRANSITION", exception.getCode());
  }

  @Test
  @DisplayName("Should handle dot-notation error codes")
  void shouldHandleDotNotationErrorCodes() {
    var exception = new BusinessException("Validation failed", "validation.amount.negative");

    assertEquals("validation.amount.negative", exception.getCode());
  }

  @Test
  @DisplayName("Should handle numeric error codes")
  void shouldHandleNumericErrorCodes() {
    var exception = new BusinessException("Application error", "ERR_1001");

    assertEquals("ERR_1001", exception.getCode());
  }
}
