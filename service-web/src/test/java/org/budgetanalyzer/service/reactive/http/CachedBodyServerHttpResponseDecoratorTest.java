package org.budgetanalyzer.service.reactive.http;

import static org.assertj.core.api.Assertions.assertThat;

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
    assertThat(cachedBody).isEqualTo(responseBody);
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
    assertThat(cachedBody).isEqualTo(responseBody);
  }

  @Test
  void shouldHandleEmptyResponse() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);

    // Act - Don't write anything
    var cachedBody = decorator.getCachedBody();

    // Assert - Should return empty string
    assertThat(cachedBody).isEqualTo("");
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
    assertThat(cachedBody.length() >= maxSize).isTrue();
    assertThat(cachedBody.contains("TRUNCATED")).isTrue();
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
    assertThat(cachedBody.endsWith("... [TRUNCATED]")).isTrue();
    assertThat(cachedBody.length() <= maxSize + 20).isTrue(); // Accounting for truncation message
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
    assertThat(cachedBody).isEqualTo("Hello World");
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
    assertThat(cachedBody).isEqualTo(jsonResponse);
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
    assertThat(cachedBody).isEqualTo(responseBody);
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
    assertThat(cachedBody.startsWith("12345")).isTrue();
    assertThat(cachedBody.contains("TRUNCATED")).isTrue();
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
    assertThat(cachedBody).isEqualTo("Part 1. Part 2. Part 3.");
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
    assertThat(cachedBody).isEqualTo(jsonResponse);
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
    assertThat(cachedBody.contains("TRUNCATED") || cachedBody.isEmpty()).isTrue();
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
    assertThat(decorator.getCachedBody()).isEqualTo(responseBody);
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

    // Assert - Should NOT be truncated since it's exactly max size in bytes
    assertThat(cachedBody).isEqualTo(responseBody);
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
    assertThat(cachedBody).isEqualTo("");
  }

  @Test
  void shouldPreserveOriginalResponseHeaders() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    originalResponse.getHeaders().add("X-Custom-Header", "custom-value");

    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);

    // Act & Assert - Headers should be accessible through decorator
    assertThat(decorator.getHeaders().getFirst("X-Custom-Header")).isEqualTo("custom-value");
  }

  @Test
  void shouldReturnCorrectCachedBodySize() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);
    var responseBody = "Hello World";
    var bodyBuffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));

    // Act
    decorator.writeWith(Flux.just(bodyBuffer)).block();

    // Assert
    assertThat(decorator.getCachedBodySize()).isEqualTo(responseBody.length());
  }

  @Test
  void shouldCountUtf8ResponseSizeInBytesAtExactBoundary() {
    var originalResponse = new MockServerHttpResponse();
    var responseBody = "éé";
    var maxSize = responseBody.getBytes(StandardCharsets.UTF_8).length;
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, maxSize);
    var bodyBuffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));

    decorator.writeWith(Flux.just(bodyBuffer)).block();

    assertThat(decorator.getCachedBody()).isEqualTo(responseBody);
    assertThat(decorator.getCachedBodySize()).isEqualTo(maxSize);
  }

  @Test
  void shouldTruncateUtf8ResponseUsingByteLimit() {
    var originalResponse = new MockServerHttpResponse();
    var responseBody = "ééé";
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 5);
    var bodyBuffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));

    decorator.writeWith(Flux.just(bodyBuffer)).block();

    var cachedBody = decorator.getCachedBody();
    assertThat(cachedBody.startsWith("éé")).isTrue();
    assertThat(cachedBody.contains("TRUNCATED")).isTrue();
    assertThat(decorator.getCachedBodySize()).isEqualTo(5);
  }

  @Test
  void shouldReturnZeroSizeForEmptyBody() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);

    // Act - Don't write anything

    // Assert
    assertThat(decorator.getCachedBodySize()).isEqualTo(0);
  }

  @Test
  void shouldReturnTruncatedSizeForLargeBody() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var maxSize = 50;
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, maxSize);
    var responseBody = "x".repeat(100); // 100 bytes
    var bodyBuffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));

    // Act
    decorator.writeWith(Flux.just(bodyBuffer)).block();

    // Assert - Size should be capped at maxSize
    assertThat(decorator.getCachedBodySize()).isEqualTo(maxSize);
  }
}
