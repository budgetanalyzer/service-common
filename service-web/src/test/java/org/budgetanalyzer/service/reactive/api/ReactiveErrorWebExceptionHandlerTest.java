package org.budgetanalyzer.service.reactive.api;

import static org.assertj.core.api.Assertions.assertThat;

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

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(responseBody.path("type").asText()).isEqualTo(ApiErrorType.INTERNAL_ERROR.name());
    assertThat(responseBody.path("message").asText()).isEqualTo("An unexpected error occurred");
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

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(responseBody.path("type").asText()).isEqualTo(ApiErrorType.NOT_FOUND.name());
    assertThat(responseBody.path("message").asText()).isEqualTo("Missing resource");
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

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(exchange.getResponse().getHeaders().getAllow())
        .isEqualTo(Set.of(HttpMethod.GET, HttpMethod.PUT));
    assertThat(responseBody.path("type").asText()).isEqualTo(ApiErrorType.INVALID_REQUEST.name());
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

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(responseBody.path("type").asText()).isEqualTo(ApiErrorType.UNAUTHORIZED.name());
    assertThat(responseBody.path("message").asText()).isEqualTo("Authentication required");
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

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(responseBody.path("type").asText()).isEqualTo(ApiErrorType.FORBIDDEN.name());
    assertThat(responseBody.path("message").asText())
        .isEqualTo("You do not have permission to perform this action");
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

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(responseBody.path("type").asText()).isEqualTo(ApiErrorType.VALIDATION_ERROR.name());
    assertThat(responseBody.path("message").asText()).isEqualTo("Validation failed for 2 field(s)");
    assertThat(responseBody.path("fieldErrors").size()).isEqualTo(2);
    assertThat(responseBody.path("fieldErrors").get(0).path("field").asText()).isEqualTo("name");
    assertThat(responseBody.path("fieldErrors").get(1).path("field").asText()).isEqualTo("age");
  }

  @Test
  @DisplayName("Should fall through when response is already committed")
  void shouldFallThroughWhenResponseIsAlreadyCommitted() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
    var exception = new IllegalStateException("Boom");

    StepVerifier.create(exchange.getResponse().setComplete()).verifyComplete();

    StepVerifier.create(reactiveErrorWebExceptionHandler.handle(exchange, exception))
        .expectErrorSatisfies(throwable -> assertThat(throwable).isSameAs(exception))
        .verify();
  }

  @Test
  @DisplayName("Should write application json content type")
  void shouldWriteApplicationJsonContentType() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

    StepVerifier.create(
            reactiveErrorWebExceptionHandler.handle(exchange, new IllegalArgumentException("Bad")))
        .verifyComplete();

    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_JSON);
  }

  @Test
  @DisplayName("Should use order negative one")
  void shouldUseOrderNegativeOne() {
    assertThat(reactiveErrorWebExceptionHandler.getOrder()).isEqualTo(-1);
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

    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_JSON);
  }

  private record TestRequest(String name, int age) {}
}
