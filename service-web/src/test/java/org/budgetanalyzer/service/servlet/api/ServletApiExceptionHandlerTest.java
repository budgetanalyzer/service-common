package org.budgetanalyzer.service.servlet.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.api.ApiErrorType;
import org.budgetanalyzer.service.api.ApiExceptionHandler;
import org.budgetanalyzer.service.api.FieldError;
import org.budgetanalyzer.service.exception.BusinessException;
import org.budgetanalyzer.service.exception.ClientException;
import org.budgetanalyzer.service.exception.InvalidRequestException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.exception.ServiceException;
import org.budgetanalyzer.service.exception.ServiceUnavailableException;

/** Unit tests for {@link ServletApiExceptionHandler}. */
@DisplayName("ServletApiExceptionHandler Tests")
class ServletApiExceptionHandlerTest {

  private ServletApiExceptionHandler servletApiExceptionHandler;
  private WebRequest webRequest;

  @BeforeEach
  void setUp() {
    servletApiExceptionHandler = new ServletApiExceptionHandler();
    // WebRequest is not used in the handler implementation, so we can pass null
    webRequest = null;
  }

  @Test
  @DisplayName("Should handle InvalidRequestException with INVALID_REQUEST type")
  void shouldHandleInvalidRequestException() {
    var exception = new InvalidRequestException("Invalid request format");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.INVALID_REQUEST);
    assertThat(response.getMessage()).isEqualTo("Invalid request format");
    assertThat(response.getCode()).isNull();
    assertThat(response.getFieldErrors()).isNull();
  }

  @Test
  @DisplayName("Should handle MethodArgumentNotValidException with VALIDATION_ERROR type")
  void shouldHandleMethodArgumentNotValidException() throws Exception {
    var payload = new TestPayload("", 25);
    var bindingResult = new BeanPropertyBindingResult(payload, "testPayload");
    bindingResult.addError(
        new org.springframework.validation.FieldError(
            "testPayload", "name", "", false, null, null, "must not be blank"));
    var methodParameter = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);
    var exception = new MethodArgumentNotValidException(methodParameter, bindingResult);

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.VALIDATION_ERROR);
    assertThat(response.getMessage()).isEqualTo("Validation failed for 1 field(s)");
    assertThat(response.getFieldErrors()).isNotNull();
    assertThat(response.getFieldErrors().size()).isEqualTo(1);
    assertThat(response.getFieldErrors().get(0).getField()).isEqualTo("name");
    assertThat(response.getFieldErrors().get(0).getMessage()).isEqualTo("must not be blank");
    assertThat(response.getFieldErrors().get(0).getRejectedValue()).isEqualTo("");
  }

  @Test
  @DisplayName("Should handle ResourceNotFoundException with NOT_FOUND type")
  void shouldHandleResourceNotFoundException() {
    var exception = new ResourceNotFoundException("Transaction not found with id: 123");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.NOT_FOUND);
    assertThat(response.getMessage()).isEqualTo("Transaction not found with id: 123");
    assertThat(response.getCode()).isNull();
    assertThat(response.getFieldErrors()).isNull();
  }

  @Test
  @DisplayName("Should handle NoHandlerFoundException with NOT_FOUND type")
  void shouldHandleNoHandlerFoundException() {
    var exception = new NoHandlerFoundException("GET", "/api/nonexistent", null);

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.NOT_FOUND);
    assertThat(response.getMessage()).isNotNull();
    assertThat(response.getCode()).isNull();
    assertThat(response.getFieldErrors()).isNull();
  }

  @Test
  @DisplayName("Should handle BusinessException with APPLICATION_ERROR type and code")
  void shouldHandleBusinessException() {
    var exception = new BusinessException("Amount must be positive", "NEGATIVE_AMOUNT");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.APPLICATION_ERROR);
    assertThat(response.getMessage()).isEqualTo("Amount must be positive");
    assertThat(response.getCode()).isEqualTo("NEGATIVE_AMOUNT");
    assertThat(response.getFieldErrors()).isNull();
  }

  @Test
  @DisplayName("Should handle BusinessException with null code")
  void shouldHandleBusinessExceptionWithNullCode() {
    var exception = new BusinessException("Business rule violation", null);

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.APPLICATION_ERROR);
    assertThat(response.getMessage()).isEqualTo("Business rule violation");
    assertThat(response.getCode()).isNull();
  }

  @Test
  @DisplayName("Should handle BusinessException with field errors")
  void shouldHandleBusinessExceptionWithFieldErrors() {
    var fieldErrors =
        List.of(
            FieldError.of(0, "amount", "must not be null", null),
            FieldError.of(2, "date", "must be a valid date", "invalid-date"));
    var exception =
        new BusinessException("Batch validation failed", "BATCH_VALIDATION_FAILED", fieldErrors);

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.APPLICATION_ERROR);
    assertThat(response.getMessage()).isEqualTo("Batch validation failed");
    assertThat(response.getCode()).isEqualTo("BATCH_VALIDATION_FAILED");
    assertThat(response.getFieldErrors()).isNotNull();
    assertThat(response.getFieldErrors().size()).isEqualTo(2);
    assertThat(response.getFieldErrors().get(0).getIndex()).isEqualTo(Integer.valueOf(0));
    assertThat(response.getFieldErrors().get(0).getField()).isEqualTo("amount");
    assertThat(response.getFieldErrors().get(0).getMessage()).isEqualTo("must not be null");
  }

  @Test
  @DisplayName("Should handle BusinessException without field errors")
  void shouldHandleBusinessExceptionWithoutFieldErrors() {
    var exception = new BusinessException("Simple business error", "SIMPLE_ERROR");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.APPLICATION_ERROR);
    // Field errors should be null or empty when not provided
    assertThat(response.getFieldErrors()).isNullOrEmpty();
  }

  @Test
  @DisplayName("Should handle ClientException with SERVICE_UNAVAILABLE type")
  void shouldHandleClientException() {
    var exception = new ClientException("External API failed");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.SERVICE_UNAVAILABLE);
    assertThat(response.getMessage()).isEqualTo("External API failed");
    assertThat(response.getCode()).isNull();
  }

  @Test
  @DisplayName("Should handle ServiceUnavailableException with SERVICE_UNAVAILABLE type")
  void shouldHandleServiceUnavailableException() {
    var exception = new ServiceUnavailableException("Database connection failed");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.SERVICE_UNAVAILABLE);
    assertThat(response.getMessage()).isEqualTo("Database connection failed");
    assertThat(response.getCode()).isNull();
  }

  @Test
  @DisplayName("Should handle MethodArgumentTypeMismatchException with INVALID_REQUEST type")
  void shouldHandleMethodArgumentTypeMismatchException() throws NoSuchMethodException {
    // Simulate type mismatch exception (e.g., passing "abc" for Long parameter)
    var methodParameter = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);
    var exception =
        new MethodArgumentTypeMismatchException(
            "abc",
            Long.class,
            "id",
            methodParameter,
            new NumberFormatException("For input string: \"abc\""));

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.INVALID_REQUEST);
    assertThat(response.getMessage()).isNotNull();
  }

  @Test
  @DisplayName("Should handle MissingServletRequestPartException with INVALID_REQUEST type")
  void shouldHandleMissingServletRequestPartException() {
    var exception = new MissingServletRequestPartException("file");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.INVALID_REQUEST);
    assertThat(response.getMessage()).isNotNull();
  }

  @Test
  @DisplayName("Should handle MissingServletRequestParameterException with INVALID_REQUEST type")
  void shouldHandleMissingServletRequestParameterException() {
    var exception = new MissingServletRequestParameterException("userId", "Long");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.INVALID_REQUEST);
    assertThat(response.getMessage()).isNotNull();
  }

  @Test
  @DisplayName("Should handle AccessDeniedException with FORBIDDEN type and 403")
  void shouldHandleAccessDeniedExceptionWith403() {
    var exception = new AccessDeniedException("Forbidden");

    var response = servletApiExceptionHandler.handle(exception);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.FORBIDDEN);
    assertThat(response.getMessage())
        .isEqualTo("You do not have permission to perform this action");
    assertThat(response.getCode()).isNull();
    assertThat(response.getFieldErrors()).isNull();
  }

  @Test
  @DisplayName("Should handle AuthorizationDeniedException with FORBIDDEN type and 403")
  void shouldHandleAuthorizationDeniedExceptionWith403() {
    var exception = new AuthorizationDeniedException("Access Denied");

    var response = servletApiExceptionHandler.handle(exception);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.FORBIDDEN);
    assertThat(response.getMessage())
        .isEqualTo("You do not have permission to perform this action");
    assertThat(response.getCode()).isNull();
    assertThat(response.getFieldErrors()).isNull();
  }

  @Test
  @DisplayName("Should handle AuthenticationException with UNAUTHORIZED type and 401")
  void shouldHandleAuthenticationExceptionWith401() {
    var exception = new BadCredentialsException("Bad credentials");

    var response = servletApiExceptionHandler.handle(exception);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.UNAUTHORIZED);
    assertThat(response.getMessage()).isEqualTo("Authentication required");
    assertThat(response.getCode()).isNull();
    assertThat(response.getFieldErrors()).isNull();
  }

  @Test
  @DisplayName("Should handle generic Exception with INTERNAL_ERROR type")
  void shouldHandleGenericException() {
    var exception = new Exception("Unexpected error occurred");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.INTERNAL_ERROR);
    assertThat(response.getMessage()).isEqualTo("An unexpected error occurred");
    assertThat(response.getCode()).isNull();
  }

  @Test
  @DisplayName("Should handle RuntimeException with INTERNAL_ERROR type")
  void shouldHandleRuntimeException() {
    var exception = new RuntimeException("Runtime error");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.INTERNAL_ERROR);
    assertThat(response.getMessage()).isEqualTo("An unexpected error occurred");
  }

  @Test
  @DisplayName("Should handle IOException with INTERNAL_ERROR type")
  void shouldHandleIoException() {
    var exception = new IOException("IO error occurred");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.INTERNAL_ERROR);
    assertThat(response.getMessage()).isEqualTo("An unexpected error occurred");
  }

  @Test
  @DisplayName("Should handle ServiceException with INTERNAL_ERROR type")
  void shouldHandleServiceException() {
    var exception = new ServiceException("Service error");

    var response = servletApiExceptionHandler.handle((Exception) exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.INTERNAL_ERROR);
    assertThat(response.getMessage()).isEqualTo("An unexpected error occurred");
  }

  @Test
  @DisplayName("Should handle exception with null message")
  void shouldHandleExceptionWithNullMessage() {
    var exception = new InvalidRequestException(null);

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.INVALID_REQUEST);
    assertThat(response.getMessage()).isNull();
  }

  @Test
  @DisplayName("Should handle exception with empty message")
  void shouldHandleExceptionWithEmptyMessage() {
    var exception = new ResourceNotFoundException("");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.NOT_FOUND);
    assertThat(response.getMessage()).isEqualTo("");
  }

  @Test
  @DisplayName("Should handle exception with cause")
  void shouldHandleExceptionWithCause() {
    var cause = new IOException("Network error");
    var exception = new ServiceUnavailableException("Service down", cause);

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.SERVICE_UNAVAILABLE);
    assertThat(response.getMessage()).isEqualTo("Service down");
  }

  @Test
  @DisplayName("Should handle exception with nested cause chain")
  void shouldHandleExceptionWithNestedCauseChain() {
    var rootCause = new IllegalArgumentException("Invalid argument");
    var intermediateCause = new IOException("IO error", rootCause);
    var exception = new ClientException("Client error", intermediateCause);

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.SERVICE_UNAVAILABLE);
    assertThat(response.getMessage()).isEqualTo("Client error");
  }

  @Test
  @DisplayName("Should preserve error message from BusinessException")
  void shouldPreserveErrorMessageFromBusinessException() {
    var detailedMessage = "Budget limit of $1000.00 exceeded by $250.50";
    var exception = new BusinessException(detailedMessage, "BUDGET_EXCEEDED");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response.getMessage()).isEqualTo(detailedMessage);
    assertThat(response.getCode()).isEqualTo("BUDGET_EXCEEDED");
  }

  @Test
  @DisplayName("Should handle InvalidRequestException with cause")
  void shouldHandleInvalidRequestExceptionWithCause() {
    var parseException = new NumberFormatException("Cannot parse 'abc' as number");
    var exception = new InvalidRequestException("Invalid number format", parseException);

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.INVALID_REQUEST);
    assertThat(response.getMessage()).isEqualTo("Invalid number format");
  }

  @Test
  @DisplayName("Should handle multiple exceptions of same type independently")
  void shouldHandleMultipleExceptionsOfSameTypeIndependently() {
    var exception1 = new ResourceNotFoundException("User not found");
    var exception2 = new ResourceNotFoundException("Product not found");

    var response1 = servletApiExceptionHandler.handle(exception1, webRequest);
    var response2 = servletApiExceptionHandler.handle(exception2, webRequest);

    assertThat(response1.getMessage()).isEqualTo("User not found");
    assertThat(response2.getMessage()).isEqualTo("Product not found");
  }

  @Test
  @DisplayName("Should handle NullPointerException as internal error")
  void shouldHandleNullPointerExceptionAsInternalError() {
    var exception = new NullPointerException("Null value encountered");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.INTERNAL_ERROR);
    assertThat(response.getMessage()).isEqualTo("An unexpected error occurred");
  }

  @Test
  @DisplayName("Should handle IllegalArgumentException as internal error")
  void shouldHandleIllegalArgumentExceptionAsInternalError() {
    var exception = new IllegalArgumentException("Invalid argument provided");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.INTERNAL_ERROR);
    assertThat(response.getMessage()).isEqualTo("An unexpected error occurred");
  }

  @Test
  @DisplayName("Should handle IllegalStateException as internal error")
  void shouldHandleIllegalStateExceptionAsInternalError() {
    var exception = new IllegalStateException("Invalid state");

    var response = servletApiExceptionHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.INTERNAL_ERROR);
    assertThat(response.getMessage()).isEqualTo("An unexpected error occurred");
  }

  @Test
  @DisplayName("Should handle ResponseStatusException with 401 and use generic message")
  void shouldHandleResponseStatusException401() {
    var exception = new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key expired");

    var response = servletApiExceptionHandler.handle(exception);

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getType()).isEqualTo(ApiErrorType.UNAUTHORIZED);
    assertThat(body.getMessage()).isEqualTo("Authentication required");
  }

  @Test
  @DisplayName("Should handle ResponseStatusException with 400 and reason")
  void shouldHandleResponseStatusException400WithReason() {
    var exception = new ResponseStatusException(HttpStatus.BAD_REQUEST, "accessToken is required");

    var response = servletApiExceptionHandler.handle(exception);

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getType()).isEqualTo(ApiErrorType.INVALID_REQUEST);
    assertThat(body.getMessage()).isEqualTo("accessToken is required");
  }

  @Test
  @DisplayName("Should handle ResponseStatusException with 404")
  void shouldHandleResponseStatusException404() {
    var exception = new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");

    var response = servletApiExceptionHandler.handle(exception);

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getType()).isEqualTo(ApiErrorType.NOT_FOUND);
    assertThat(body.getMessage()).isEqualTo("Resource not found");
  }

  @Test
  @DisplayName("Should handle ResponseStatusException with 500")
  void shouldHandleResponseStatusException500() {
    var exception =
        new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");

    var response = servletApiExceptionHandler.handle(exception);

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getType()).isEqualTo(ApiErrorType.INTERNAL_ERROR);
    assertThat(body.getMessage()).isEqualTo("Something went wrong");
  }

  @Test
  @DisplayName("Should handle ResponseStatusException with 503 and SERVICE_UNAVAILABLE type")
  void shouldHandleResponseStatusException503() {
    var exception =
        new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Downstream service down");

    var response = servletApiExceptionHandler.handle(exception);

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getType()).isEqualTo(ApiErrorType.SERVICE_UNAVAILABLE);
    assertThat(body.getMessage()).isEqualTo("Downstream service down");
  }

  @Test
  @DisplayName("Should handle ResponseStatusException with generic 4xx as INVALID_REQUEST type")
  void shouldHandleResponseStatusExceptionGeneric4xx() {
    var exception = new ResponseStatusException(HttpStatus.CONFLICT, "Resource already exists");

    var response = servletApiExceptionHandler.handle(exception);

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getType()).isEqualTo(ApiErrorType.INVALID_REQUEST);
    assertThat(body.getMessage()).isEqualTo("Resource already exists");
  }

  @Test
  @DisplayName("Should handle ResponseStatusException with 403 and use generic message")
  void shouldHandleResponseStatusException403() {
    var exception = new ResponseStatusException(HttpStatus.FORBIDDEN, "User lacks admin scope");

    var response = servletApiExceptionHandler.handle(exception);

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getType()).isEqualTo(ApiErrorType.FORBIDDEN);
    assertThat(body.getMessage()).isEqualTo("You do not have permission to perform this action");
  }

  @Test
  @DisplayName("Should delegate common exception handling to shared resolver")
  void shouldDelegateCommonExceptionHandlingToSharedResolver() {
    var resolvedError =
        new ApiExceptionHandler.ResolvedError(
            HttpStatus.NOT_FOUND,
            ApiErrorResponse.builder()
                .type(ApiErrorType.NOT_FOUND)
                .message("shared not found")
                .build());
    var trackingHandler = new TrackingServletApiExceptionHandler(resolvedError, null);
    var exception = new ResourceNotFoundException("controller-level message");

    var response = trackingHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.NOT_FOUND);
    assertThat(response.getMessage()).isEqualTo("shared not found");
    assertThat(trackingHandler.commonResolvedThrowable).isSameAs(exception);
    assertThat(trackingHandler.resolveCommonExceptionInvocationCount).isEqualTo(1);
  }

  @Test
  @DisplayName("Should delegate ResponseStatusException handling to shared resolver")
  void shouldDelegateResponseStatusExceptionHandlingToSharedResolver() {
    var resolvedError =
        new ApiExceptionHandler.ResolvedError(
            HttpStatus.BAD_REQUEST,
            ApiErrorResponse.builder()
                .type(ApiErrorType.INVALID_REQUEST)
                .message("shared invalid request")
                .build());
    var trackingHandler = new TrackingServletApiExceptionHandler(resolvedError, null);
    var exception = new ResponseStatusException(HttpStatus.BAD_REQUEST, "original reason");

    var response = trackingHandler.handle(exception);

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).isEqualTo("shared invalid request");
    assertThat(trackingHandler.commonResolvedThrowable).isSameAs(exception);
    assertThat(trackingHandler.resolveCommonExceptionInvocationCount).isEqualTo(1);
  }

  @Test
  @DisplayName("Should delegate validation handling to shared validation resolver")
  void shouldDelegateValidationHandlingToSharedValidationResolver() throws Exception {
    var bindingResult = new BeanPropertyBindingResult(new TestPayload("", 0), "testPayload");
    var resolvedError =
        new ApiExceptionHandler.ResolvedError(
            HttpStatus.BAD_REQUEST,
            ApiErrorResponse.builder()
                .type(ApiErrorType.VALIDATION_ERROR)
                .message("shared validation")
                .fieldErrors(List.of(FieldError.of("name", "must not be blank", "")))
                .build());
    var trackingHandler = new TrackingServletApiExceptionHandler(null, resolvedError);
    var exception =
        new MethodArgumentNotValidException(
            new MethodParameter(Object.class.getMethod("equals", Object.class), 0), bindingResult);

    var response = trackingHandler.handle(exception, webRequest);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(ApiErrorType.VALIDATION_ERROR);
    assertThat(response.getMessage()).isEqualTo("shared validation");
    assertThat(response.getFieldErrors()).isNotNull();
    assertThat(response.getFieldErrors().size()).isEqualTo(1);
    assertThat(trackingHandler.validationResolvedBindingResult).isSameAs(bindingResult);
    assertThat(trackingHandler.resolveValidationFailureInvocationCount).isEqualTo(1);
  }

  /** Test payload for validation tests. */
  private static class TestPayload {
    private final String name;
    private final int age;

    private TestPayload(String name, int age) {
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

  private static final class TrackingServletApiExceptionHandler extends ServletApiExceptionHandler {

    private final ApiExceptionHandler.ResolvedError commonResolvedError;
    private final ApiExceptionHandler.ResolvedError validationResolvedError;
    private Throwable commonResolvedThrowable;
    private BindingResult validationResolvedBindingResult;
    private int resolveCommonExceptionInvocationCount;
    private int resolveValidationFailureInvocationCount;

    private TrackingServletApiExceptionHandler(
        ApiExceptionHandler.ResolvedError commonResolvedError,
        ApiExceptionHandler.ResolvedError validationResolvedError) {
      this.commonResolvedError = commonResolvedError;
      this.validationResolvedError = validationResolvedError;
    }

    @Override
    public ApiExceptionHandler.ResolvedError resolveCommonException(Throwable throwable) {
      commonResolvedThrowable = throwable;
      resolveCommonExceptionInvocationCount++;
      return commonResolvedError == null
          ? super.resolveCommonException(throwable)
          : commonResolvedError;
    }

    @Override
    public ApiExceptionHandler.ResolvedError resolveValidationFailure(BindingResult bindingResult) {
      validationResolvedBindingResult = bindingResult;
      resolveValidationFailureInvocationCount++;
      return validationResolvedError == null
          ? super.resolveValidationFailure(bindingResult)
          : validationResolvedError;
    }
  }
}
