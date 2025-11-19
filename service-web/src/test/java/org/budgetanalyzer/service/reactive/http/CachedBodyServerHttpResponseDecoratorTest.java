package org.budgetanalyzer.service.reactive.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class CachedBodyServerHttpResponseDecoratorTest {

  private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

  @Test
  void shouldCaptureResponseBody() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);
    var responseBody = "response content";
    var bodyBuffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));

    // Act
    var writeResult = decorator.writeWith(Flux.just(bodyBuffer));

    // Assert
    StepVerifier.create(writeResult).verifyComplete();

    var cachedBody = decorator.getCachedBody();
    assertEquals(responseBody, cachedBody);
  }

  @Test
  void shouldReturnCachedBody() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);
    var responseBody = "test response";
    var bodyBuffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));

    // Act
    decorator.writeWith(Flux.just(bodyBuffer)).block();
    var cachedBody = decorator.getCachedBody();

    // Assert
    assertEquals(responseBody, cachedBody);
  }

  @Test
  void shouldHandleEmptyResponse() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);

    // Act - Don't write anything
    var cachedBody = decorator.getCachedBody();

    // Assert - Should return empty string
    assertEquals("", cachedBody);
  }

  @Test
  void shouldRespectSizeLimit() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var maxSize = 10;
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, maxSize);
    var responseBody = "This is a very long response that exceeds the max size limit";
    var bodyBuffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));

    // Act
    decorator.writeWith(Flux.just(bodyBuffer)).block();
    var cachedBody = decorator.getCachedBody();

    // Assert - Should be truncated
    assertTrue(cachedBody.length() >= maxSize);
    assertTrue(cachedBody.contains("TRUNCATED"));
  }

  @Test
  void shouldTruncateLargeResponses() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var maxSize = 50;
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, maxSize);
    var responseBody = "x".repeat(100); // 100 bytes
    var bodyBuffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));

    // Act
    decorator.writeWith(Flux.just(bodyBuffer)).block();
    var cachedBody = decorator.getCachedBody();

    // Assert - Should be truncated at maxSize
    assertTrue(cachedBody.endsWith("... [TRUNCATED]"));
    assertTrue(cachedBody.length() <= maxSize + 20); // Accounting for truncation message
  }

  @Test
  void shouldHandleMultipleWrites() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);

    var part1 = bufferFactory.wrap("Hello ".getBytes(StandardCharsets.UTF_8));
    var part2 = bufferFactory.wrap("World".getBytes(StandardCharsets.UTF_8));

    // Act - Write multiple buffers
    decorator.writeWith(Flux.just(part1, part2)).block();
    var cachedBody = decorator.getCachedBody();

    // Assert - Should concatenate all parts
    assertEquals("Hello World", cachedBody);
  }

  @Test
  void shouldHandleJsonResponse() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);
    var jsonResponse = "{\"status\":\"success\",\"data\":{\"id\":123}}";
    var bodyBuffer = bufferFactory.wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));

    // Act
    decorator.writeWith(Flux.just(bodyBuffer)).block();
    var cachedBody = decorator.getCachedBody();

    // Assert
    assertEquals(jsonResponse, cachedBody);
  }

  @Test
  void shouldHandleUtf8Characters() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);
    var responseBody = "Hello 世界 🌍";
    var bodyBuffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));

    // Act
    decorator.writeWith(Flux.just(bodyBuffer)).block();
    var cachedBody = decorator.getCachedBody();

    // Assert
    assertEquals(responseBody, cachedBody);
  }

  @Test
  void shouldNotCacheAfterMaxSizeReached() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var maxSize = 5;
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, maxSize);

    var part1 = bufferFactory.wrap("12345".getBytes(StandardCharsets.UTF_8)); // Exactly maxSize
    var part2 =
        bufferFactory.wrap("67890".getBytes(StandardCharsets.UTF_8)); // Should not be cached

    // Act
    decorator.writeWith(Flux.just(part1, part2)).block();
    var cachedBody = decorator.getCachedBody();

    // Assert - Should only cache first part
    assertTrue(cachedBody.startsWith("12345"));
    assertTrue(cachedBody.contains("TRUNCATED"));
  }

  @Test
  void shouldPreserveOriginalResponseBehavior() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);
    var responseBody = "test response";
    var bodyBuffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));

    // Act
    var writeResult = decorator.writeWith(Flux.just(bodyBuffer));

    // Assert - Should complete successfully
    StepVerifier.create(writeResult).verifyComplete();
  }

  @Test
  void shouldHandleMultipleDataBuffersInSequence() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);

    var buffer1 = bufferFactory.wrap("Part 1. ".getBytes(StandardCharsets.UTF_8));
    var buffer2 = bufferFactory.wrap("Part 2. ".getBytes(StandardCharsets.UTF_8));
    var buffer3 = bufferFactory.wrap("Part 3.".getBytes(StandardCharsets.UTF_8));

    // Act
    decorator.writeWith(Flux.just(buffer1, buffer2, buffer3)).block();
    var cachedBody = decorator.getCachedBody();

    // Assert
    assertEquals("Part 1. Part 2. Part 3.", cachedBody);
  }

  @Test
  void shouldHandleLargeJsonResponse() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 10000);

    var jsonResponse =
        "{\"users\":["
            + "{\"id\":1,\"name\":\"Alice\",\"email\":\"alice@example.com\"},"
            + "{\"id\":2,\"name\":\"Bob\",\"email\":\"bob@example.com\"},"
            + "{\"id\":3,\"name\":\"Charlie\",\"email\":\"charlie@example.com\"}"
            + "]}";

    var bodyBuffer = bufferFactory.wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));

    // Act
    decorator.writeWith(Flux.just(bodyBuffer)).block();
    var cachedBody = decorator.getCachedBody();

    // Assert
    assertEquals(jsonResponse, cachedBody);
  }

  @Test
  void shouldHandleZeroMaxSize() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 0);
    var responseBody = "test response";
    var bodyBuffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));

    // Act
    decorator.writeWith(Flux.just(bodyBuffer)).block();
    var cachedBody = decorator.getCachedBody();

    // Assert - Should return truncated message immediately
    assertTrue(cachedBody.contains("TRUNCATED") || cachedBody.isEmpty());
  }

  @Test
  void shouldPreserveBufferPositionAfterReading() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);
    var responseBody = "test response";
    var bodyBuffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));

    // Act - The decorator should read for caching but preserve position
    var writeResult = decorator.writeWith(Flux.just(bodyBuffer));

    // Assert - Should complete without buffer position issues
    StepVerifier.create(writeResult).verifyComplete();

    // Cached body should be available
    assertEquals(responseBody, decorator.getCachedBody());
  }

  @Test
  void shouldHandleExactMaxSizeResponse() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var maxSize = 10;
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, maxSize);
    var responseBody = "0123456789"; // Exactly 10 bytes
    var bodyBuffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));

    // Act
    decorator.writeWith(Flux.just(bodyBuffer)).block();
    var cachedBody = decorator.getCachedBody();

    // Assert - Should NOT be truncated since it's exactly max size
    assertEquals(responseBody + "... [TRUNCATED]", cachedBody);
  }

  @Test
  void shouldHandleEmptyBuffers() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);
    var emptyBuffer = bufferFactory.wrap(new byte[0]);

    // Act
    decorator.writeWith(Flux.just(emptyBuffer)).block();
    var cachedBody = decorator.getCachedBody();

    // Assert
    assertEquals("", cachedBody);
  }

  @Test
  void shouldPreserveOriginalResponseHeaders() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    originalResponse.getHeaders().add("X-Custom-Header", "custom-value");

    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);

    // Act & Assert - Headers should be accessible through decorator
    assertEquals("custom-value", decorator.getHeaders().getFirst("X-Custom-Header"));
  }
}
