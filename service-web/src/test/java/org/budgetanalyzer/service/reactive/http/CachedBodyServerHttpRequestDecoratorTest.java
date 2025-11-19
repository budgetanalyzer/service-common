package org.budgetanalyzer.service.reactive.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class CachedBodyServerHttpRequestDecoratorTest {

  private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

  @Test
  void shouldCacheRequestBodyForMultipleReads() {
    // Arrange
    var requestBody = "test body content";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.just(bodyBuffer));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act - Read body multiple times
    var firstRead = decorator.getBody().collectList();
    var secondRead = decorator.getBody().collectList();

    // Assert - Both reads should succeed
    StepVerifier.create(firstRead).assertNext(buffers -> assertNotNull(buffers)).verifyComplete();

    StepVerifier.create(secondRead).assertNext(buffers -> assertNotNull(buffers)).verifyComplete();
  }

  @Test
  void shouldReturnCachedBodyAsString() {
    // Arrange
    var requestBody = "test body content";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.just(bodyBuffer));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act
    var result = decorator.getCachedBodyAsString(1000);

    // Assert
    StepVerifier.create(result).expectNext(requestBody).verifyComplete();
  }

  @Test
  void shouldHandleEmptyBody() {
    // Arrange
    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.empty());

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act
    var result = decorator.getCachedBodyAsString(1000);

    // Assert - Should return empty string
    StepVerifier.create(result).expectNext("").verifyComplete();
  }

  @Test
  void shouldPreserveOriginalHeaders() {
    // Arrange
    var requestBody = "test body content";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var originalRequest =
        MockServerHttpRequest.post("/test")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Custom-Header", "custom-value")
            .body(Flux.just(bodyBuffer));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act
    var headers = decorator.getHeaders();

    // Assert
    assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
    assertEquals("custom-value", headers.getFirst("X-Custom-Header"));
  }

  @Test
  void shouldHandleUtf8Encoding() {
    // Arrange
    var requestBody = "Hello 世界 🌍";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var originalRequest =
        MockServerHttpRequest.post("/test")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Flux.just(bodyBuffer));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act
    var result = decorator.getCachedBodyAsString(1000);

    // Assert
    StepVerifier.create(result).expectNext(requestBody).verifyComplete();
  }

  @Test
  void shouldHandleCustomCharset() {
    // Arrange
    var requestBody = "test body content";
    var charset = StandardCharsets.ISO_8859_1;
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(charset));

    var originalRequest =
        MockServerHttpRequest.post("/test")
            .contentType(new MediaType("text", "plain", charset))
            .body(Flux.just(bodyBuffer));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act
    var result = decorator.getCachedBodyAsString(1000);

    // Assert - Should use charset from content type
    StepVerifier.create(result).expectNext(requestBody).verifyComplete();
  }

  @Test
  void shouldDefaultToUtf8WhenNoCharsetSpecified() {
    // Arrange
    var requestBody = "test body content";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var originalRequest =
        MockServerHttpRequest.post("/test")
            .contentType(MediaType.APPLICATION_JSON) // No charset
            .body(Flux.just(bodyBuffer));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act
    var result = decorator.getCachedBodyAsString(1000);

    // Assert - Should default to UTF-8
    StepVerifier.create(result).expectNext(requestBody).verifyComplete();
  }

  @Test
  void shouldTruncateBodyAtMaxBytes() {
    // Arrange
    var requestBody = "This is a very long request body that exceeds the max size limit";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.just(bodyBuffer));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act - Limit to 10 bytes
    var result = decorator.getCachedBodyAsString(10);

    // Assert - Should truncate and include message
    StepVerifier.create(result)
        .assertNext(
            body -> {
              assertTrue(body.startsWith("This is a "));
              assertTrue(body.contains("TRUNCATED"));
            })
        .verifyComplete();
  }

  @Test
  void shouldHandleLargeBody() {
    // Arrange
    var requestBody = "x".repeat(10000); // 10KB body
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.just(bodyBuffer));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act
    var result = decorator.getCachedBodyAsString(20000); // Allow full read

    // Assert
    StepVerifier.create(result).expectNext(requestBody).verifyComplete();
  }

  @Test
  void shouldHandleMultipleDataBuffers() {
    // Arrange
    var part1 = bufferFactory.wrap("Hello ".getBytes(StandardCharsets.UTF_8));
    var part2 = bufferFactory.wrap("World".getBytes(StandardCharsets.UTF_8));

    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.just(part1, part2));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act
    var result = decorator.getCachedBodyAsString(1000);

    // Assert - Should join all parts
    StepVerifier.create(result).expectNext("Hello World").verifyComplete();
  }

  @Test
  void shouldHandleJsonBody() {
    // Arrange
    var jsonBody = "{\"name\":\"John\",\"age\":30}";
    var bodyBuffer = bufferFactory.wrap(jsonBody.getBytes(StandardCharsets.UTF_8));

    var originalRequest =
        MockServerHttpRequest.post("/test")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Flux.just(bodyBuffer));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act
    var result = decorator.getCachedBodyAsString(1000);

    // Assert
    StepVerifier.create(result).expectNext(jsonBody).verifyComplete();
  }

  @Test
  void shouldAllowMultipleSubscribersToBody() {
    // Arrange
    var requestBody = "test body content";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.just(bodyBuffer));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act - Subscribe multiple times (simulates logging + handler)
    var subscriber1 = decorator.getBody().collectList();
    var subscriber2 = decorator.getBody().collectList();
    var subscriber3 = decorator.getCachedBodyAsString(1000);

    // Assert - All subscribers should receive data
    StepVerifier.create(subscriber1)
        .assertNext(buffers -> assertTrue(buffers.size() > 0))
        .verifyComplete();

    StepVerifier.create(subscriber2)
        .assertNext(buffers -> assertTrue(buffers.size() > 0))
        .verifyComplete();

    StepVerifier.create(subscriber3).expectNext(requestBody).verifyComplete();
  }

  @Test
  void shouldHandleZeroMaxBytes() {
    // Arrange
    var requestBody = "test body content";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.just(bodyBuffer));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act - Max 0 bytes
    var result = decorator.getCachedBodyAsString(0);

    // Assert - Should return truncation message
    StepVerifier.create(result)
        .assertNext(body -> assertTrue(body.contains("TRUNCATED") || body.isEmpty()))
        .verifyComplete();
  }

  @Test
  void shouldPreserveRequestMethod() {
    // Arrange
    var requestBody = "test";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.just(bodyBuffer));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act & Assert
    assertEquals(originalRequest.getMethod(), decorator.getMethod());
  }

  @Test
  void shouldPreserveRequestUri() {
    // Arrange
    var requestBody = "test";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));

    var originalRequest =
        MockServerHttpRequest.post("/test/path?param=value").body(Flux.just(bodyBuffer));

    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest);

    // Act & Assert
    assertEquals(originalRequest.getURI(), decorator.getURI());
  }
}
