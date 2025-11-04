package com.bleurubin.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.bleurubin.service.exception.BusinessException;
import com.bleurubin.service.exception.ClientException;
import com.bleurubin.service.exception.InvalidRequestException;
import com.bleurubin.service.exception.ResourceNotFoundException;
import com.bleurubin.service.exception.ServiceException;
import com.bleurubin.service.exception.ServiceUnavailableException;

/** Unit tests for {@link DefaultApiExceptionHandler}. */
@DisplayName("DefaultApiExceptionHandler Tests")
class DefaultApiExceptionHandlerTest {

  private DefaultApiExceptionHandler handler;
  private WebRequest webRequest;

  @BeforeEach
  void setUp() {
    handler = new DefaultApiExceptionHandler();
    // WebRequest is not used in the handler implementation, so we can pass null
    webRequest = null;
  }

  @Test
  @DisplayName("Should handle InvalidRequestException with INVALID_REQUEST type")
  void shouldHandleInvalidRequestException() {
    var exception = new InvalidRequestException("Invalid request format");

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.INVALID_REQUEST, response.getType());
    assertEquals("Invalid request format", response.getMessage());
    assertNull(response.getCode());
    assertNull(response.getFieldErrors());
  }

  @Test
  @DisplayName("Should handle ResourceNotFoundException with NOT_FOUND type")
  void shouldHandleResourceNotFoundException() {
    var exception = new ResourceNotFoundException("Transaction not found with id: 123");

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.NOT_FOUND, response.getType());
    assertEquals("Transaction not found with id: 123", response.getMessage());
    assertNull(response.getCode());
    assertNull(response.getFieldErrors());
  }

  @Test
  @DisplayName("Should handle BusinessException with APPLICATION_ERROR type and code")
  void shouldHandleBusinessException() {
    var exception = new BusinessException("Amount must be positive", "NEGATIVE_AMOUNT");

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.APPLICATION_ERROR, response.getType());
    assertEquals("Amount must be positive", response.getMessage());
    assertEquals("NEGATIVE_AMOUNT", response.getCode());
    assertNull(response.getFieldErrors());
  }

  @Test
  @DisplayName("Should handle BusinessException with null code")
  void shouldHandleBusinessExceptionWithNullCode() {
    var exception = new BusinessException("Business rule violation", null);

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.APPLICATION_ERROR, response.getType());
    assertEquals("Business rule violation", response.getMessage());
    assertNull(response.getCode());
  }

  @Test
  @DisplayName("Should handle ClientException with SERVICE_UNAVAILABLE type")
  void shouldHandleClientException() {
    var exception = new ClientException("External API failed");

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.SERVICE_UNAVAILABLE, response.getType());
    assertEquals("External API failed", response.getMessage());
    assertNull(response.getCode());
  }

  @Test
  @DisplayName("Should handle ServiceUnavailableException with SERVICE_UNAVAILABLE type")
  void shouldHandleServiceUnavailableException() {
    var exception = new ServiceUnavailableException("Database connection failed");

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.SERVICE_UNAVAILABLE, response.getType());
    assertEquals("Database connection failed", response.getMessage());
    assertNull(response.getCode());
  }

  @Test
  @DisplayName("Should handle MethodArgumentTypeMismatchException with INVALID_REQUEST type")
  void shouldHandleMethodArgumentTypeMismatchException() {
    // Simulate type mismatch exception (e.g., passing "abc" for Long parameter)
    var exception =
        new MethodArgumentTypeMismatchException(
            "abc",
            Long.class,
            "id",
            null, // MethodParameter can be null in test context
            new NumberFormatException("For input string: \"abc\""));

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.INVALID_REQUEST, response.getType());
    assertNotNull(response.getMessage());
  }

  @Test
  @DisplayName("Should handle MissingServletRequestPartException with INVALID_REQUEST type")
  void shouldHandleMissingServletRequestPartException() {
    var exception = new MissingServletRequestPartException("file");

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.INVALID_REQUEST, response.getType());
    assertNotNull(response.getMessage());
  }

  @Test
  @DisplayName("Should handle generic Exception with INTERNAL_ERROR type")
  void shouldHandleGenericException() {
    var exception = new Exception("Unexpected error occurred");

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.INTERNAL_ERROR, response.getType());
    assertEquals("Unexpected error occurred", response.getMessage());
    assertNull(response.getCode());
  }

  @Test
  @DisplayName("Should handle RuntimeException with INTERNAL_ERROR type")
  void shouldHandleRuntimeException() {
    var exception = new RuntimeException("Runtime error");

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.INTERNAL_ERROR, response.getType());
    assertEquals("Runtime error", response.getMessage());
  }

  @Test
  @DisplayName("Should handle IOException with INTERNAL_ERROR type")
  void shouldHandleIoException() {
    var exception = new IOException("IO error occurred");

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.INTERNAL_ERROR, response.getType());
    assertEquals("IO error occurred", response.getMessage());
  }

  @Test
  @DisplayName("Should handle ServiceException with INTERNAL_ERROR type")
  void shouldHandleServiceException() {
    var exception = new ServiceException("Service error");

    var response = handler.handle((Exception) exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.INTERNAL_ERROR, response.getType());
    assertEquals("Service error", response.getMessage());
  }

  @Test
  @DisplayName("Should handle exception with null message")
  void shouldHandleExceptionWithNullMessage() {
    var exception = new InvalidRequestException(null);

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.INVALID_REQUEST, response.getType());
    assertNull(response.getMessage());
  }

  @Test
  @DisplayName("Should handle exception with empty message")
  void shouldHandleExceptionWithEmptyMessage() {
    var exception = new ResourceNotFoundException("");

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.NOT_FOUND, response.getType());
    assertEquals("", response.getMessage());
  }

  @Test
  @DisplayName("Should handle exception with cause")
  void shouldHandleExceptionWithCause() {
    var cause = new IOException("Network error");
    var exception = new ServiceUnavailableException("Service down", cause);

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.SERVICE_UNAVAILABLE, response.getType());
    assertEquals("Service down", response.getMessage());
  }

  @Test
  @DisplayName("Should handle exception with nested cause chain")
  void shouldHandleExceptionWithNestedCauseChain() {
    var rootCause = new IllegalArgumentException("Invalid argument");
    var intermediateCause = new IOException("IO error", rootCause);
    var exception = new ClientException("Client error", intermediateCause);

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.SERVICE_UNAVAILABLE, response.getType());
    assertEquals("Client error", response.getMessage());
  }

  @Test
  @DisplayName("Should preserve error message from BusinessException")
  void shouldPreserveErrorMessageFromBusinessException() {
    var detailedMessage = "Budget limit of $1000.00 exceeded by $250.50";
    var exception = new BusinessException(detailedMessage, "BUDGET_EXCEEDED");

    var response = handler.handle(exception, webRequest);

    assertEquals(detailedMessage, response.getMessage());
    assertEquals("BUDGET_EXCEEDED", response.getCode());
  }

  @Test
  @DisplayName("Should handle InvalidRequestException with cause")
  void shouldHandleInvalidRequestExceptionWithCause() {
    var parseException = new NumberFormatException("Cannot parse 'abc' as number");
    var exception = new InvalidRequestException("Invalid number format", parseException);

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.INVALID_REQUEST, response.getType());
    assertEquals("Invalid number format", response.getMessage());
  }

  @Test
  @DisplayName("Should handle multiple exceptions of same type independently")
  void shouldHandleMultipleExceptionsOfSameTypeIndependently() {
    var exception1 = new ResourceNotFoundException("User not found");
    var exception2 = new ResourceNotFoundException("Product not found");

    var response1 = handler.handle(exception1, webRequest);
    var response2 = handler.handle(exception2, webRequest);

    assertEquals("User not found", response1.getMessage());
    assertEquals("Product not found", response2.getMessage());
  }

  @Test
  @DisplayName("Should handle NullPointerException as internal error")
  void shouldHandleNullPointerExceptionAsInternalError() {
    var exception = new NullPointerException("Null value encountered");

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.INTERNAL_ERROR, response.getType());
    assertEquals("Null value encountered", response.getMessage());
  }

  @Test
  @DisplayName("Should handle IllegalArgumentException as internal error")
  void shouldHandleIllegalArgumentExceptionAsInternalError() {
    var exception = new IllegalArgumentException("Invalid argument provided");

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.INTERNAL_ERROR, response.getType());
    assertEquals("Invalid argument provided", response.getMessage());
  }

  @Test
  @DisplayName("Should handle IllegalStateException as internal error")
  void shouldHandleIllegalStateExceptionAsInternalError() {
    var exception = new IllegalStateException("Invalid state");

    var response = handler.handle(exception, webRequest);

    assertNotNull(response);
    assertEquals(ApiErrorType.INTERNAL_ERROR, response.getType());
    assertEquals("Invalid state", response.getMessage());
  }
}
