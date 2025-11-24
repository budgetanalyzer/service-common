package org.budgetanalyzer.service.reactive.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;

import reactor.test.StepVerifier;

import org.budgetanalyzer.service.api.ApiErrorType;
import org.budgetanalyzer.service.exception.BusinessException;
import org.budgetanalyzer.service.exception.ClientException;
import org.budgetanalyzer.service.exception.InvalidRequestException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.exception.ServiceException;
import org.budgetanalyzer.service.exception.ServiceUnavailableException;

/** Unit tests for {@link ReactiveApiExceptionHandler}. */
@DisplayName("ReactiveApiExceptionHandler Tests")
class ReactiveApiExceptionHandlerTest {

  private ReactiveApiExceptionHandler reactiveApiExceptionHandler;

  @BeforeEach
  void setUp() {
    reactiveApiExceptionHandler = new ReactiveApiExceptionHandler();
  }

  @Test
  @DisplayName("Should handle InvalidRequestException with INVALID_REQUEST type")
  void shouldHandleInvalidRequestException() {
    var exception = new InvalidRequestException("Invalid request format");

    var result = reactiveApiExceptionHandler.handleInvalidRequest(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.INVALID_REQUEST, body.getType());
              assertEquals("Invalid request format", body.getMessage());
              assertNull(body.getCode());
              assertNull(body.getFieldErrors());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle ResourceNotFoundException with NOT_FOUND type")
  void shouldHandleResourceNotFoundException() {
    var exception = new ResourceNotFoundException("Transaction not found with id: 123");

    var result = reactiveApiExceptionHandler.handleNotFound(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.NOT_FOUND, body.getType());
              assertEquals("Transaction not found with id: 123", body.getMessage());
              assertNull(body.getCode());
              assertNull(body.getFieldErrors());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle BusinessException with APPLICATION_ERROR type and code")
  void shouldHandleBusinessException() {
    var exception = new BusinessException("Amount must be positive", "NEGATIVE_AMOUNT");

    var result = reactiveApiExceptionHandler.handleBusiness(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.APPLICATION_ERROR, body.getType());
              assertEquals("Amount must be positive", body.getMessage());
              assertEquals("NEGATIVE_AMOUNT", body.getCode());
              assertNull(body.getFieldErrors());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle BusinessException with null code")
  void shouldHandleBusinessExceptionWithNullCode() {
    var exception = new BusinessException("Business rule violation", null);

    var result = reactiveApiExceptionHandler.handleBusiness(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.APPLICATION_ERROR, body.getType());
              assertEquals("Business rule violation", body.getMessage());
              assertNull(body.getCode());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle ClientException with SERVICE_UNAVAILABLE type")
  void shouldHandleClientException() {
    var exception = new ClientException("External API failed");

    var result = reactiveApiExceptionHandler.handleClientException(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.SERVICE_UNAVAILABLE, body.getType());
              assertEquals("External API failed", body.getMessage());
              assertNull(body.getCode());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle ServiceUnavailableException with SERVICE_UNAVAILABLE type")
  void shouldHandleServiceUnavailableException() {
    var exception = new ServiceUnavailableException("Database connection failed");

    var result = reactiveApiExceptionHandler.handleServiceUnavailable(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.SERVICE_UNAVAILABLE, body.getType());
              assertEquals("Database connection failed", body.getMessage());
              assertNull(body.getCode());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle WebExchangeBindException with VALIDATION_ERROR type and field errors")
  void shouldHandleWebExchangeBindException() throws Exception {
    var target = new TestDto("", -1);
    var bindingResult = new BeanPropertyBindingResult(target, "testDto");
    bindingResult.addError(
        new FieldError("testDto", "name", "", false, null, null, "must not be blank"));
    bindingResult.addError(
        new FieldError("testDto", "age", -1, false, null, null, "must be positive"));

    // Create a MethodParameter for the exception
    var method = TestDto.class.getMethod("getName");
    var methodParam =
        new org.springframework.core.MethodParameter(method, -1); // -1 for return type
    var exception = new WebExchangeBindException(methodParam, bindingResult);

    var result = reactiveApiExceptionHandler.handleValidation(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.VALIDATION_ERROR, body.getType());
              assertNotNull(body.getFieldErrors());
              assertEquals(2, body.getFieldErrors().size());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle WebExchangeBindException with single field error")
  void shouldHandleWebExchangeBindExceptionWithSingleFieldError() throws Exception {
    var target = new TestDto("", 25);
    var bindingResult = new BeanPropertyBindingResult(target, "testDto");
    bindingResult.addError(
        new FieldError("testDto", "name", "", false, null, null, "must not be blank"));

    // Create a MethodParameter for the exception
    var method = TestDto.class.getMethod("getName");
    var methodParam =
        new org.springframework.core.MethodParameter(method, -1); // -1 for return type
    var exception = new WebExchangeBindException(methodParam, bindingResult);

    var result = reactiveApiExceptionHandler.handleValidation(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.VALIDATION_ERROR, body.getType());
              assertNotNull(body.getFieldErrors());
              assertEquals(1, body.getFieldErrors().size());
              assertEquals("name", body.getFieldErrors().get(0).getField());
              assertEquals("must not be blank", body.getFieldErrors().get(0).getMessage());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle generic Exception with INTERNAL_ERROR type")
  void shouldHandleGenericException() {
    var exception = new Exception("Unexpected error occurred");

    var result = reactiveApiExceptionHandler.handleGenericException(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.INTERNAL_ERROR, body.getType());
              assertEquals("An unexpected error occurred", body.getMessage());
              assertNull(body.getCode());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle RuntimeException with INTERNAL_ERROR type")
  void shouldHandleRuntimeException() {
    var exception = new RuntimeException("Runtime error");

    var result = reactiveApiExceptionHandler.handleGenericException(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.INTERNAL_ERROR, body.getType());
              assertEquals("An unexpected error occurred", body.getMessage());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle IOException with INTERNAL_ERROR type")
  void shouldHandleIoException() {
    var exception = new IOException("IO error occurred");

    var result = reactiveApiExceptionHandler.handleGenericException(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.INTERNAL_ERROR, body.getType());
              assertEquals("An unexpected error occurred", body.getMessage());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle ServiceException with INTERNAL_ERROR type")
  void shouldHandleServiceException() {
    var exception = new ServiceException("Service error");

    var result = reactiveApiExceptionHandler.handleGenericException(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.INTERNAL_ERROR, body.getType());
              assertEquals("An unexpected error occurred", body.getMessage());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle exception with null message")
  void shouldHandleExceptionWithNullMessage() {
    var exception = new InvalidRequestException(null);

    var result = reactiveApiExceptionHandler.handleInvalidRequest(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.INVALID_REQUEST, body.getType());
              assertNull(body.getMessage());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle exception with empty message")
  void shouldHandleExceptionWithEmptyMessage() {
    var exception = new ResourceNotFoundException("");

    var result = reactiveApiExceptionHandler.handleNotFound(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.NOT_FOUND, body.getType());
              assertEquals("", body.getMessage());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle exception with cause")
  void shouldHandleExceptionWithCause() {
    var cause = new IOException("Network error");
    var exception = new ServiceUnavailableException("Service down", cause);

    var result = reactiveApiExceptionHandler.handleServiceUnavailable(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.SERVICE_UNAVAILABLE, body.getType());
              assertEquals("Service down", body.getMessage());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle exception with nested cause chain")
  void shouldHandleExceptionWithNestedCauseChain() {
    var rootCause = new IllegalArgumentException("Invalid argument");
    var intermediateCause = new IOException("IO error", rootCause);
    var exception = new ClientException("Client error", intermediateCause);

    var result = reactiveApiExceptionHandler.handleClientException(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.SERVICE_UNAVAILABLE, body.getType());
              assertEquals("Client error", body.getMessage());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should preserve error message from BusinessException")
  void shouldPreserveErrorMessageFromBusinessException() {
    var detailedMessage = "Budget limit of $1000.00 exceeded by $250.50";
    var exception = new BusinessException(detailedMessage, "BUDGET_EXCEEDED");

    var result = reactiveApiExceptionHandler.handleBusiness(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(detailedMessage, body.getMessage());
              assertEquals("BUDGET_EXCEEDED", body.getCode());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle InvalidRequestException with cause")
  void shouldHandleInvalidRequestExceptionWithCause() {
    var parseException = new NumberFormatException("Cannot parse 'abc' as number");
    var exception = new InvalidRequestException("Invalid number format", parseException);

    var result = reactiveApiExceptionHandler.handleInvalidRequest(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.INVALID_REQUEST, body.getType());
              assertEquals("Invalid number format", body.getMessage());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle multiple exceptions of same type independently")
  void shouldHandleMultipleExceptionsOfSameTypeIndependently() {
    var exception1 = new ResourceNotFoundException("User not found");
    var exception2 = new ResourceNotFoundException("Product not found");

    var result1 = reactiveApiExceptionHandler.handleNotFound(exception1);
    var result2 = reactiveApiExceptionHandler.handleNotFound(exception2);

    StepVerifier.create(result1)
        .assertNext(
            response -> {
              assertEquals("User not found", response.getBody().getMessage());
            })
        .verifyComplete();

    StepVerifier.create(result2)
        .assertNext(
            response -> {
              assertEquals("Product not found", response.getBody().getMessage());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle NullPointerException as internal error")
  void shouldHandleNullPointerExceptionAsInternalError() {
    var exception = new NullPointerException("Null value encountered");

    var result = reactiveApiExceptionHandler.handleGenericException(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.INTERNAL_ERROR, body.getType());
              assertEquals("An unexpected error occurred", body.getMessage());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle IllegalArgumentException as internal error")
  void shouldHandleIllegalArgumentExceptionAsInternalError() {
    var exception = new IllegalArgumentException("Invalid argument provided");

    var result = reactiveApiExceptionHandler.handleGenericException(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.INTERNAL_ERROR, body.getType());
              assertEquals("An unexpected error occurred", body.getMessage());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle IllegalStateException as internal error")
  void shouldHandleIllegalStateExceptionAsInternalError() {
    var exception = new IllegalStateException("Invalid state");

    var result = reactiveApiExceptionHandler.handleGenericException(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.INTERNAL_ERROR, body.getType());
              assertEquals("An unexpected error occurred", body.getMessage());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Should handle WebExchangeBindException with global errors")
  void shouldHandleWebExchangeBindExceptionWithGlobalErrors() throws Exception {
    var target = new TestDto("valid", 25);
    var bindingResult = new BeanPropertyBindingResult(target, "testDto");
    // Add a global error (not field-specific)
    bindingResult.reject("global.error", "Global validation failed");
    bindingResult.addError(
        new FieldError("testDto", "name", "valid", false, null, null, "name error"));

    // Create a MethodParameter for the exception
    var method = TestDto.class.getMethod("getName");
    var methodParam =
        new org.springframework.core.MethodParameter(method, -1); // -1 for return type
    var exception = new WebExchangeBindException(methodParam, bindingResult);

    var result = reactiveApiExceptionHandler.handleValidation(exception);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
              var body = response.getBody();
              assertNotNull(body);
              assertEquals(ApiErrorType.VALIDATION_ERROR, body.getType());
              // Should only include field errors, not global errors
              assertNotNull(body.getFieldErrors());
              assertEquals(1, body.getFieldErrors().size());
            })
        .verifyComplete();
  }

  /** Test DTO for validation tests. */
  private static class TestDto {
    private String name;
    private int age;

    public TestDto(String name, int age) {
      this.name = name;
      this.age = age;
    }

    public String getName() {
      return name;
    }

    public int getAge() {
      return age;
    }
  }
}
