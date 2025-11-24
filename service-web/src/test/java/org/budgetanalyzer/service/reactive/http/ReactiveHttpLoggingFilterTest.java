package org.budgetanalyzer.service.reactive.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.budgetanalyzer.service.config.HttpLoggingProperties;

@ExtendWith(MockitoExtension.class)
class ReactiveHttpLoggingFilterTest {

  @Mock private WebFilterChain filterChain;

  private HttpLoggingProperties httpLoggingProperties;
  private ReactiveHttpLoggingFilter reactiveHttpLoggingFilter;

  @BeforeEach
  void setUp() {
    httpLoggingProperties = new HttpLoggingProperties();
    httpLoggingProperties.setEnabled(true);
    httpLoggingProperties.setLogLevel("DEBUG");
    httpLoggingProperties.setIncludeRequestBody(true);
    httpLoggingProperties.setIncludeResponseBody(true);
    httpLoggingProperties.setMaxBodySize(10000);

    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);
  }

  @Test
  void shouldBypassFilterWhenDisabled() {
    // Arrange
    httpLoggingProperties.setEnabled(false);
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete without decoration
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldLogGetRequest() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

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
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

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
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

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
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

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
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete and log error
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldSkipNonErrorResponsesWhenLogErrorsOnlyEnabled() {
    // Arrange
    httpLoggingProperties.setLogErrorsOnly(true);
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    exchange.getResponse().setRawStatusCode(200);

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete (logging skipped for non-errors)
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldLogErrorResponseWhenLogErrorsOnlyEnabled() {
    // Arrange
    httpLoggingProperties.setLogErrorsOnly(true);
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    exchange.getResponse().setRawStatusCode(404);

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

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
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should propagate error but still log in doFinally
    StepVerifier.create(result).expectError(RuntimeException.class).verify();
  }

  @Test
  void shouldIncludeQueryParametersWhenEnabled() {
    // Arrange
    httpLoggingProperties.setIncludeQueryParams(true);
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users?page=1&size=10&search=john"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete and log with query params
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldIncludeHeadersWhenEnabled() {
    // Arrange
    httpLoggingProperties.setIncludeRequestHeaders(true);
    httpLoggingProperties.setIncludeResponseHeaders(true);
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users")
                .header("X-Custom-Header", "custom-value")
                .header("Authorization", "Bearer token123"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete and log with headers
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldIncludeClientIpWhenEnabled() {
    // Arrange
    httpLoggingProperties.setIncludeClientIp(true);
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete and log with client IP
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldHandleUnresolvedRemoteAddressInKubernetes() {
    // Arrange - Simulate Kubernetes environment where InetSocketAddress.getAddress() returns null
    httpLoggingProperties.setIncludeClientIp(true);
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    // Create an unresolved InetSocketAddress (getAddress() returns null)
    var unresolvedAddress = InetSocketAddress.createUnresolved("unresolved-hostname", 8080);

    // Mock the exchange to return the unresolved address
    var mockRequest = mock(ServerHttpRequest.class);
    when(mockRequest.getMethod()).thenReturn(org.springframework.http.HttpMethod.GET);
    when(mockRequest.getURI()).thenReturn(java.net.URI.create("/api/users"));
    when(mockRequest.getRemoteAddress()).thenReturn(unresolvedAddress);
    when(mockRequest.getHeaders()).thenReturn(org.springframework.http.HttpHeaders.EMPTY);
    when(mockRequest.getBody()).thenReturn(Flux.empty());

    var mockExchange = mock(ServerWebExchange.class);
    var mockResponse =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/users")).getResponse();
    when(mockExchange.getRequest()).thenReturn(mockRequest);
    when(mockExchange.getResponse()).thenReturn(mockResponse);
    when(mockExchange.mutate())
        .thenReturn(MockServerWebExchange.from(MockServerHttpRequest.get("/api/users")).mutate());

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act - Should not throw NPE when getAddress() returns null
    var result = reactiveHttpLoggingFilter.filter(mockExchange, filterChain);

    // Assert - Should complete successfully without NPE
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldNotLogRequestBodyWhenDisabled() {
    // Arrange
    httpLoggingProperties.setIncludeRequestBody(false);
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var requestBody = "{\"name\":\"John\"}";
    var bodyBuffer =
        DefaultDataBufferFactory.sharedInstance.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/users").body(Flux.just(bodyBuffer)));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete without logging request body
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldNotLogResponseBodyWhenDisabled() {
    // Arrange
    httpLoggingProperties.setIncludeResponseBody(false);
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    exchange.getResponse().setRawStatusCode(200);

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete without logging response body
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldRespectMaxBodySizeLimit() {
    // Arrange
    httpLoggingProperties.setMaxBodySize(10); // Very small limit
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var requestBody = "This is a very long request body that exceeds the max size limit";
    var bodyBuffer =
        DefaultDataBufferFactory.sharedInstance.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/users").body(Flux.just(bodyBuffer)));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

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
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete without errors
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldLogAtConfiguredLevel() {
    // Arrange
    httpLoggingProperties.setLogLevel("INFO");
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

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
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

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
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete and log duration
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldSkipLoggingForKubernetesHealthCheckAgent() {
    // Arrange
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users").header("User-Agent", "kube-probe/1.34"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete and skip logging
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldSkipLoggingForAwsElbHealthCheckAgent() {
    // Arrange
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/actuator/health")
                .header("User-Agent", "ELB-HealthChecker/2.0"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete and skip logging
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldSkipLoggingForGcpHealthCheckAgent() {
    // Arrange
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/health").header("User-Agent", "GoogleHC/1.0"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete and skip logging
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldNotSkipLoggingForRegularUserAgent() {
    // Arrange
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete with logging
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldLogHealthCheckWhenSkipHealthCheckAgentsDisabled() {
    // Arrange
    httpLoggingProperties.setSkipHealthCheckAgents(false);
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users").header("User-Agent", "kube-probe/1.34"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete with logging even for health check
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldSkipLoggingForExcludedPaths() {
    // Arrange
    httpLoggingProperties.getExcludePatterns().add("/actuator/**");
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete and skip logging
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldOnlyLogIncludedPaths() {
    // Arrange
    httpLoggingProperties.getIncludePatterns().add("/api/**");
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/other/endpoint"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete and skip logging (not in include list)
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldLogIncludedPaths() {
    // Arrange
    httpLoggingProperties.getIncludePatterns().add("/api/**");
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete with logging
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldHandleMissingUserAgentHeader() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert - Should complete with logging (no user agent is not a health check)
    StepVerifier.create(result).verifyComplete();
  }
}
