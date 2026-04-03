package org.budgetanalyzer.service.reactive.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.test.StepVerifier;

import org.budgetanalyzer.service.api.ApiErrorType;

/** Unit tests for {@link ReactiveErrorWebExceptionHandler}. */
@DisplayName("ReactiveErrorWebExceptionHandler Tests")
class ReactiveErrorWebExceptionHandlerTest {

  private ObjectMapper objectMapper;
  private ReactiveErrorWebExceptionHandler reactiveErrorWebExceptionHandler;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    reactiveErrorWebExceptionHandler = new ReactiveErrorWebExceptionHandler(objectMapper);
  }

  @Test
  @DisplayName("Should return internal error for generic exception")
  void shouldReturnInternalErrorForGenericException() throws Exception {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

    StepVerifier.create(
            reactiveErrorWebExceptionHandler.handle(exchange, new IllegalStateException("Boom")))
        .verifyComplete();

    var responseBody = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
    assertEquals(ApiErrorType.INTERNAL_ERROR.name(), responseBody.path("type").asText());
    assertEquals("An unexpected error occurred", responseBody.path("message").asText());
  }

  @Test
  @DisplayName("Should preserve not found status from ResponseStatusException")
  void shouldPreserveNotFoundStatusFromResponseStatusException() throws Exception {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

    StepVerifier.create(
            reactiveErrorWebExceptionHandler.handle(
                exchange, new ResponseStatusException(HttpStatus.NOT_FOUND, "Missing resource")))
        .verifyComplete();

    var responseBody = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());

    assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    assertEquals(ApiErrorType.NOT_FOUND.name(), responseBody.path("type").asText());
    assertEquals("Missing resource", responseBody.path("message").asText());
  }

  @Test
  @DisplayName("Should preserve ResponseStatusException headers")
  void shouldPreserveResponseStatusExceptionHeaders() throws Exception {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/test"));
    var exception =
        new MethodNotAllowedException(HttpMethod.POST, List.of(HttpMethod.GET, HttpMethod.PUT));

    StepVerifier.create(reactiveErrorWebExceptionHandler.handle(exchange, exception))
        .verifyComplete();

    var responseBody = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());

    assertEquals(HttpStatus.METHOD_NOT_ALLOWED, exchange.getResponse().getStatusCode());
    assertEquals(
        Set.of(HttpMethod.GET, HttpMethod.PUT), exchange.getResponse().getHeaders().getAllow());
    assertEquals(ApiErrorType.INVALID_REQUEST.name(), responseBody.path("type").asText());
  }

  @Test
  @DisplayName("Should return safe unauthorized message for authentication exception")
  void shouldReturnSafeUnauthorizedMessageForAuthenticationException() throws Exception {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

    StepVerifier.create(
            reactiveErrorWebExceptionHandler.handle(
                exchange, new BadCredentialsException("Token expired")))
        .verifyComplete();

    var responseBody = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    assertEquals(ApiErrorType.UNAUTHORIZED.name(), responseBody.path("type").asText());
    assertEquals("Authentication required", responseBody.path("message").asText());
  }

  @Test
  @DisplayName("Should return safe forbidden message for access denied exception")
  void shouldReturnSafeForbiddenMessageForAccessDeniedException() throws Exception {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

    StepVerifier.create(
            reactiveErrorWebExceptionHandler.handle(
                exchange, new AccessDeniedException("Missing permission")))
        .verifyComplete();

    var responseBody = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());

    assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    assertEquals(ApiErrorType.FORBIDDEN.name(), responseBody.path("type").asText());
    assertEquals(
        "You do not have permission to perform this action", responseBody.path("message").asText());
  }

  @Test
  @DisplayName("Should return validation error for WebExchangeBindException")
  void shouldReturnValidationErrorForWebExchangeBindException() throws Exception {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
    var bindingResult = new BeanPropertyBindingResult(new TestRequest("", -1), "testRequest");
    bindingResult.addError(
        new FieldError("testRequest", "name", "", false, null, null, "must not be blank"));
    bindingResult.addError(
        new FieldError("testRequest", "age", -1, false, null, null, "must be positive"));

    var method = TestRequest.class.getMethod("name");
    var methodParameter = new MethodParameter(method, -1);
    var exception = new WebExchangeBindException(methodParameter, bindingResult);

    StepVerifier.create(reactiveErrorWebExceptionHandler.handle(exchange, exception))
        .verifyComplete();

    var responseBody = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());

    assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
    assertEquals(ApiErrorType.VALIDATION_ERROR.name(), responseBody.path("type").asText());
    assertEquals("Validation failed for 2 field(s)", responseBody.path("message").asText());
    assertEquals(2, responseBody.path("fieldErrors").size());
    assertEquals("name", responseBody.path("fieldErrors").get(0).path("field").asText());
    assertEquals("age", responseBody.path("fieldErrors").get(1).path("field").asText());
  }

  @Test
  @DisplayName("Should fall through when response is already committed")
  void shouldFallThroughWhenResponseIsAlreadyCommitted() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
    var exception = new IllegalStateException("Boom");

    StepVerifier.create(exchange.getResponse().setComplete()).verifyComplete();

    StepVerifier.create(reactiveErrorWebExceptionHandler.handle(exchange, exception))
        .expectErrorSatisfies(throwable -> assertSame(exception, throwable))
        .verify();
  }

  @Test
  @DisplayName("Should write application json content type")
  void shouldWriteApplicationJsonContentType() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

    StepVerifier.create(
            reactiveErrorWebExceptionHandler.handle(exchange, new IllegalArgumentException("Bad")))
        .verifyComplete();

    assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
  }

  @Test
  @DisplayName("Should use order negative one")
  void shouldUseOrderNegativeOne() {
    assertEquals(-1, reactiveErrorWebExceptionHandler.getOrder());
  }

  @Test
  @DisplayName("Should not write html content type")
  void shouldNotWriteHtmlContentType() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE));

    StepVerifier.create(
            reactiveErrorWebExceptionHandler.handle(exchange, new IllegalArgumentException("Bad")))
        .verifyComplete();

    assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
  }

  private record TestRequest(String name, int age) {}
}
