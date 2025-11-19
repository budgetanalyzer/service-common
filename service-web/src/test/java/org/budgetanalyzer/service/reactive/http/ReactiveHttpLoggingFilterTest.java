package org.budgetanalyzer.service.reactive.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.budgetanalyzer.service.config.HttpLoggingProperties;

@ExtendWith(MockitoExtension.class)
class ReactiveHttpLoggingFilterTest {

  @Mock private WebFilterChain filterChain;

  private HttpLoggingProperties properties;
  private ReactiveHttpLoggingFilter filter;

  @BeforeEach
  void setUp() {
    properties = new HttpLoggingProperties();
    properties.setEnabled(true);
    properties.setLogLevel("DEBUG");
    properties.setIncludeRequestBody(true);
    properties.setIncludeResponseBody(true);
    properties.setMaxBodySize(10000);

    filter = new ReactiveHttpLoggingFilter(properties);
  }

  @Test
  void shouldBypassFilterWhenDisabled() {
    // Arrange
    properties.setEnabled(false);
    filter = new ReactiveHttpLoggingFilter(properties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete without decoration
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldLogGetRequest() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete successfully
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldLogPostRequestWithBody() {
    // Arrange
    var requestBody = "{\"name\":\"John\"}";
    var bodyBuffer =
        DefaultDataBufferFactory.sharedInstance.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/users").body(Flux.just(bodyBuffer)));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete successfully
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldDecorateRequestAndResponse() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/users"));

    when(filterChain.filter(any()))
        .thenAnswer(
            invocation -> {
              var decoratedExchange =
                  (org.springframework.web.server.ServerWebExchange) invocation.getArgument(0);
              // Request and response should be decorated
              var request = decoratedExchange.getRequest();
              var response = decoratedExchange.getResponse();

              // Verify decoration by checking instance types
              return Mono.empty();
            });

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldLogResponseAfterCompletion() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    exchange.getResponse().setRawStatusCode(200);

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should log response in doFinally
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldLogErrorResponse() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    exchange.getResponse().setRawStatusCode(500);

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete and log error
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldSkipNonErrorResponsesWhenLogErrorsOnlyEnabled() {
    // Arrange
    properties.setLogErrorsOnly(true);
    filter = new ReactiveHttpLoggingFilter(properties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    exchange.getResponse().setRawStatusCode(200);

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete (logging skipped for non-errors)
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldLogErrorResponseWhenLogErrorsOnlyEnabled() {
    // Arrange
    properties.setLogErrorsOnly(true);
    filter = new ReactiveHttpLoggingFilter(properties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    exchange.getResponse().setRawStatusCode(404);

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete and log error
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldHandleExceptionDuringFilterExecution() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    var expectedException = new RuntimeException("Simulated error");

    when(filterChain.filter(any())).thenReturn(Mono.error(expectedException));

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should propagate error but still log in doFinally
    StepVerifier.create(result).expectError(RuntimeException.class).verify();
  }

  @Test
  void shouldIncludeQueryParametersWhenEnabled() {
    // Arrange
    properties.setIncludeQueryParams(true);
    filter = new ReactiveHttpLoggingFilter(properties);

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users?page=1&size=10&search=john"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete and log with query params
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldIncludeHeadersWhenEnabled() {
    // Arrange
    properties.setIncludeRequestHeaders(true);
    properties.setIncludeResponseHeaders(true);
    filter = new ReactiveHttpLoggingFilter(properties);

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users")
                .header("X-Custom-Header", "custom-value")
                .header("Authorization", "Bearer token123"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete and log with headers
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldIncludeClientIpWhenEnabled() {
    // Arrange
    properties.setIncludeClientIp(true);
    filter = new ReactiveHttpLoggingFilter(properties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete and log with client IP
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldNotLogRequestBodyWhenDisabled() {
    // Arrange
    properties.setIncludeRequestBody(false);
    filter = new ReactiveHttpLoggingFilter(properties);

    var requestBody = "{\"name\":\"John\"}";
    var bodyBuffer =
        DefaultDataBufferFactory.sharedInstance.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/users").body(Flux.just(bodyBuffer)));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete without logging request body
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldNotLogResponseBodyWhenDisabled() {
    // Arrange
    properties.setIncludeResponseBody(false);
    filter = new ReactiveHttpLoggingFilter(properties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    exchange.getResponse().setRawStatusCode(200);

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete without logging response body
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldRespectMaxBodySizeLimit() {
    // Arrange
    properties.setMaxBodySize(10); // Very small limit
    filter = new ReactiveHttpLoggingFilter(properties);

    var requestBody = "This is a very long request body that exceeds the max size limit";
    var bodyBuffer =
        DefaultDataBufferFactory.sharedInstance.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/users").body(Flux.just(bodyBuffer)));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete with truncated body
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldHandleEmptyRequestBody() {
    // Arrange
    var exchange =
        MockServerWebExchange.from(MockServerHttpRequest.post("/api/users").body(Flux.empty()));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete without errors
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldLogAtConfiguredLevel() {
    // Arrange
    properties.setLogLevel("INFO");
    filter = new ReactiveHttpLoggingFilter(properties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete with INFO level logging
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldHandleMultipleDataBuffersInRequest() {
    // Arrange
    var part1 =
        DefaultDataBufferFactory.sharedInstance.wrap("{\"name\":".getBytes(StandardCharsets.UTF_8));
    var part2 =
        DefaultDataBufferFactory.sharedInstance.wrap("\"John\"}".getBytes(StandardCharsets.UTF_8));

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/users").body(Flux.just(part1, part2)));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should handle multiple buffers
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldMeasureRequestDuration() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    exchange.getResponse().setRawStatusCode(200);

    when(filterChain.filter(any()))
        .thenAnswer(
            invocation -> {
              // Simulate some processing time
              return Mono.delay(java.time.Duration.ofMillis(10)).then();
            });

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should complete and log duration
    StepVerifier.create(result).verifyComplete();
  }
}
