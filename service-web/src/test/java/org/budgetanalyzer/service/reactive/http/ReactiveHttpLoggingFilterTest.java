package org.budgetanalyzer.service.reactive.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.budgetanalyzer.service.config.HttpLoggingProperties;

@ExtendWith(MockitoExtension.class)
class ReactiveHttpLoggingFilterTest {

  @Mock private WebFilterChain filterChain;

  private HttpLoggingProperties httpLoggingProperties;
  private ReactiveHttpLoggingFilter reactiveHttpLoggingFilter;
  private Logger logger;
  private ListAppender<ILoggingEvent> listAppender;
  private Level originalLogLevel;

  @BeforeEach
  void setUp() {
    httpLoggingProperties = new HttpLoggingProperties();
    httpLoggingProperties.setEnabled(true);
    httpLoggingProperties.setLogLevel("DEBUG");
    httpLoggingProperties.setIncludeRequestBody(true);
    httpLoggingProperties.setIncludeResponseBody(true);
    httpLoggingProperties.setMaxBodySize(10000);

    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);
    logger = (Logger) LoggerFactory.getLogger(ReactiveHttpLoggingFilter.class);
    originalLogLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(listAppender);
    listAppender.stop();
    logger.setLevel(originalLogLevel);
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
  void shouldRedactSensitiveJsonRequestBodiesInLogs() {
    var requestBody = "{\"username\":\"john\",\"password\":\"secret\"}";
    var bodyBuffer =
        DefaultDataBufferFactory.sharedInstance.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/users")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(Flux.just(bodyBuffer)));

    when(filterChain.filter(any()))
        .thenAnswer(
            invocation -> {
              var decoratedExchange = (ServerWebExchange) invocation.getArgument(0);
              decoratedExchange.getResponse().setStatusCode(HttpStatus.OK);
              return DataBufferUtils.join(decoratedExchange.getRequest().getBody())
                  .then(decoratedExchange.getResponse().setComplete());
            });

    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    StepVerifier.create(result).verifyComplete();

    var logOutput = loggedMessages();
    assertThat(logOutput.contains("\"password\":\"***MASKED***\"")).isTrue();
    assertThat(logOutput.contains("\"password\":\"secret\"")).isFalse();
  }

  @Test
  void shouldOmitMultipartRequestBodiesFromLogs() {
    var requestBody = "--boundary\r\ncontent";
    var bodyBuffer =
        DefaultDataBufferFactory.sharedInstance.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/upload")
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=boundary")
                .body(Flux.just(bodyBuffer)));

    when(filterChain.filter(any()))
        .thenAnswer(
            invocation -> {
              var decoratedExchange = (ServerWebExchange) invocation.getArgument(0);
              decoratedExchange.getResponse().setStatusCode(HttpStatus.OK);
              return DataBufferUtils.join(decoratedExchange.getRequest().getBody())
                  .then(decoratedExchange.getResponse().setComplete());
            });

    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    StepVerifier.create(result).verifyComplete();

    var logOutput = loggedMessages();
    assertThat(logOutput.contains("[multipart content omitted: multipart/form-data, 19 bytes]"))
        .isTrue();
    assertThat(logOutput.contains(requestBody)).isFalse();
  }

  @Test
  void shouldOmitBinaryResponseBodiesFromLogs() {
    var responseBody = new byte[] {0x01, 0x02, 0x03};
    var bodyBuffer = DefaultDataBufferFactory.sharedInstance.wrap(responseBody);
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/export"));

    when(filterChain.filter(any()))
        .thenAnswer(
            invocation -> {
              var decoratedExchange = (ServerWebExchange) invocation.getArgument(0);
              decoratedExchange.getResponse().setStatusCode(HttpStatus.OK);
              decoratedExchange
                  .getResponse()
                  .getHeaders()
                  .set(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
              return decoratedExchange.getResponse().writeWith(Mono.just(bodyBuffer));
            });

    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    StepVerifier.create(result).verifyComplete();

    var logOutput = loggedMessages();
    assertThat(logOutput.contains("[binary content omitted: application/octet-stream, 3 bytes]"))
        .isTrue();
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

              assertThat(request).isInstanceOf(CachedBodyServerHttpRequestDecorator.class);
              assertThat(response).isInstanceOf(CachedBodyServerHttpResponseDecorator.class);
              return Mono.empty();
            });

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldNotDecorateBodiesWhenBodyLoggingDisabled() {
    httpLoggingProperties.setIncludeRequestBody(false);
    httpLoggingProperties.setIncludeResponseBody(false);
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/users"));

    when(filterChain.filter(any()))
        .thenAnswer(
            invocation -> {
              var decoratedExchange = (ServerWebExchange) invocation.getArgument(0);
              assertThat(
                      decoratedExchange.getRequest()
                          instanceof CachedBodyServerHttpRequestDecorator)
                  .isFalse();
              assertThat(
                      decoratedExchange.getResponse()
                          instanceof CachedBodyServerHttpResponseDecorator)
                  .isFalse();
              decoratedExchange.getResponse().setStatusCode(HttpStatus.OK);
              return decoratedExchange.getResponse().setComplete();
            });

    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void shouldMaskSensitiveHeadersInReactiveLogs() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users")
                .header("Authorization", "Bearer secret-token")
                .header("X-Custom-Header", "custom-value"));

    when(filterChain.filter(any()))
        .thenAnswer(
            invocation -> {
              var decoratedExchange = (ServerWebExchange) invocation.getArgument(0);
              decoratedExchange.getResponse().setStatusCode(HttpStatus.OK);
              decoratedExchange.getResponse().getHeaders().add("Set-Cookie", "session=top-secret");
              return decoratedExchange.getResponse().setComplete();
            });

    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    StepVerifier.create(result).verifyComplete();

    var logOutput = loggedMessages();
    assertThat(logOutput.contains("***MASKED***")).isTrue();
    assertThat(logOutput.contains("custom-value")).isTrue();
    assertThat(logOutput.contains("Bearer secret-token")).isFalse();
    assertThat(logOutput.contains("session=top-secret")).isFalse();
  }

  @Test
  void shouldNotLeakQueryStringWhenDisabled() {
    httpLoggingProperties.setIncludeQueryParams(false);
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users?token=secret&page=1&search=john"));

    when(filterChain.filter(any()))
        .thenAnswer(
            invocation -> {
              var decoratedExchange = (ServerWebExchange) invocation.getArgument(0);
              decoratedExchange.getResponse().setStatusCode(HttpStatus.OK);
              return decoratedExchange.getResponse().setComplete();
            });

    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    StepVerifier.create(result).verifyComplete();

    var logOutput = loggedMessages();
    assertThat(logOutput.contains("/api/users")).isTrue();
    assertThat(logOutput.contains("token=secret")).isFalse();
    assertThat(logOutput.contains("page=1")).isFalse();
    assertThat(logOutput.contains("search=john")).isFalse();
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
    when(mockRequest.getMethod()).thenReturn(HttpMethod.GET);
    when(mockRequest.getURI()).thenReturn(URI.create("/api/users"));
    when(mockRequest.getRemoteAddress()).thenReturn(unresolvedAddress);
    when(mockRequest.getHeaders()).thenReturn(HttpHeaders.EMPTY);
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
              return Mono.delay(Duration.ofMillis(10)).then();
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

  @Test
  void shouldSanitizeSensitiveQueryParamsInLogs() {
    // Arrange
    httpLoggingProperties.setIncludeQueryParams(true);
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get(
                "/login/oauth2/code/idp?code=authcode123&state=csrfstate456"));

    when(filterChain.filter(any()))
        .thenAnswer(
            invocation -> {
              var decoratedExchange = (ServerWebExchange) invocation.getArgument(0);
              decoratedExchange.getResponse().setStatusCode(HttpStatus.OK);
              return decoratedExchange.getResponse().setComplete();
            });

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert
    StepVerifier.create(result).verifyComplete();

    var logOutput = loggedMessages();
    assertThat(logOutput.contains("code=***&state=***")).isTrue();
    assertThat(logOutput.contains("authcode123")).isFalse();
    assertThat(logOutput.contains("csrfstate456")).isFalse();
  }

  @Test
  void shouldSanitizeCustomSensitiveQueryParamsInLogs() {
    // Arrange
    httpLoggingProperties.setIncludeQueryParams(true);
    httpLoggingProperties.setSensitiveQueryParams(List.of("custom_key"));
    reactiveHttpLoggingFilter = new ReactiveHttpLoggingFilter(httpLoggingProperties);

    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test?custom_key=secret&page=1&code=auth123"));

    when(filterChain.filter(any()))
        .thenAnswer(
            invocation -> {
              var decoratedExchange = (ServerWebExchange) invocation.getArgument(0);
              decoratedExchange.getResponse().setStatusCode(HttpStatus.OK);
              return decoratedExchange.getResponse().setComplete();
            });

    // Act
    var result = reactiveHttpLoggingFilter.filter(exchange, filterChain);

    // Assert
    StepVerifier.create(result).verifyComplete();

    var logOutput = loggedMessages();
    assertThat(logOutput.contains("custom_key=***")).isTrue();
    assertThat(logOutput.contains("code=***")).isTrue();
    assertThat(logOutput.contains("page=1")).isTrue();
    assertThat(logOutput.contains("secret")).isFalse();
    assertThat(logOutput.contains("auth123")).isFalse();
  }

  private String loggedMessages() {
    return listAppender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .collect(Collectors.joining("\n"));
  }
}
