package org.budgetanalyzer.service.reactive.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CachedBodyServerHttpRequestDecoratorTest {

  private static final int DEFAULT_MAX_BODY_SIZE = 1_000;

  private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

  @Test
  void shouldCaptureRequestBodyAfterConsumption() {
    var requestBody = "test body content";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));
    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.just(bodyBuffer));
    var decorator =
        new CachedBodyServerHttpRequestDecorator(originalRequest, DEFAULT_MAX_BODY_SIZE);

    StepVerifier.create(readBodyAsString(decorator)).expectNext(requestBody).verifyComplete();

    assertEquals(requestBody, decorator.getCachedBodyAsString());
  }

  @Test
  void shouldReturnEmptyBodyBeforeConsumption() {
    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.empty());
    var decorator =
        new CachedBodyServerHttpRequestDecorator(originalRequest, DEFAULT_MAX_BODY_SIZE);

    assertEquals("", decorator.getCachedBodyAsString());
  }

  @Test
  void shouldPreserveOriginalHeaders() {
    var requestBody = "test body content";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));
    var originalRequest =
        MockServerHttpRequest.post("/test")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Custom-Header", "custom-value")
            .body(Flux.just(bodyBuffer));
    var decorator =
        new CachedBodyServerHttpRequestDecorator(originalRequest, DEFAULT_MAX_BODY_SIZE);

    var headers = decorator.getHeaders();

    assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
    assertEquals("custom-value", headers.getFirst("X-Custom-Header"));
  }

  @Test
  void shouldHandleUtf8Encoding() {
    var requestBody = "Hello 世界 🌍";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));
    var originalRequest =
        MockServerHttpRequest.post("/test")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Flux.just(bodyBuffer));
    var decorator =
        new CachedBodyServerHttpRequestDecorator(originalRequest, DEFAULT_MAX_BODY_SIZE);

    StepVerifier.create(readBodyAsString(decorator)).expectNext(requestBody).verifyComplete();

    assertEquals(requestBody, decorator.getCachedBodyAsString());
  }

  @Test
  void shouldHandleCustomCharset() {
    var requestBody = "test body content";
    var charset = StandardCharsets.ISO_8859_1;
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(charset));
    var originalRequest =
        MockServerHttpRequest.post("/test")
            .contentType(new MediaType("text", "plain", charset))
            .body(Flux.just(bodyBuffer));
    var decorator =
        new CachedBodyServerHttpRequestDecorator(originalRequest, DEFAULT_MAX_BODY_SIZE);

    StepVerifier.create(readBodyAsString(decorator)).expectNext(requestBody).verifyComplete();

    assertEquals(requestBody, decorator.getCachedBodyAsString());
  }

  @Test
  void shouldTruncateBodyAtMaxBytes() {
    var requestBody = "This is a very long request body that exceeds the max size limit";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));
    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.just(bodyBuffer));
    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest, 10);

    StepVerifier.create(readBodyAsString(decorator)).expectNext(requestBody).verifyComplete();

    var cachedBody = decorator.getCachedBodyAsString();
    assertTrue(cachedBody.startsWith("This is a "));
    assertTrue(cachedBody.contains("TRUNCATED"));
  }

  @Test
  void shouldHandleMultipleDataBuffers() {
    var part1 = bufferFactory.wrap("Hello ".getBytes(StandardCharsets.UTF_8));
    var part2 = bufferFactory.wrap("World".getBytes(StandardCharsets.UTF_8));
    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.just(part1, part2));
    var decorator =
        new CachedBodyServerHttpRequestDecorator(originalRequest, DEFAULT_MAX_BODY_SIZE);

    StepVerifier.create(readBodyAsString(decorator)).expectNext("Hello World").verifyComplete();

    assertEquals("Hello World", decorator.getCachedBodyAsString());
  }

  @Test
  void shouldHandleZeroMaxBytes() {
    var requestBody = "test body content";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));
    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.just(bodyBuffer));
    var decorator = new CachedBodyServerHttpRequestDecorator(originalRequest, 0);

    StepVerifier.create(readBodyAsString(decorator)).expectNext(requestBody).verifyComplete();

    assertTrue(decorator.getCachedBodyAsString().contains("TRUNCATED"));
  }

  @Test
  void shouldPreserveRequestMethod() {
    var requestBody = "test";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));
    var originalRequest = MockServerHttpRequest.post("/test").body(Flux.just(bodyBuffer));
    var decorator =
        new CachedBodyServerHttpRequestDecorator(originalRequest, DEFAULT_MAX_BODY_SIZE);

    assertEquals(originalRequest.getMethod(), decorator.getMethod());
  }

  @Test
  void shouldPreserveRequestUri() {
    var requestBody = "test";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));
    var originalRequest =
        MockServerHttpRequest.post("/test/path?param=value").body(Flux.just(bodyBuffer));
    var decorator =
        new CachedBodyServerHttpRequestDecorator(originalRequest, DEFAULT_MAX_BODY_SIZE);

    assertEquals(originalRequest.getURI(), decorator.getURI());
  }

  @Test
  void shouldCaptureBodyWithoutConsumingDownstreamRead() {
    var requestBody = "{\"user\":\"test\",\"action\":\"login\"}";
    var bodyBuffer = bufferFactory.wrap(requestBody.getBytes(StandardCharsets.UTF_8));
    var originalRequest =
        MockServerHttpRequest.post("/api/auth")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Flux.just(bodyBuffer));
    var decorator =
        new CachedBodyServerHttpRequestDecorator(originalRequest, DEFAULT_MAX_BODY_SIZE);

    StepVerifier.create(readBodyAsString(decorator)).expectNext(requestBody).verifyComplete();

    assertEquals(requestBody, decorator.getCachedBodyAsString());
  }

  private Mono<String> readBodyAsString(CachedBodyServerHttpRequestDecorator decorator) {
    return decorator
        .getBody()
        .map(this::toString)
        .collectList()
        .map(parts -> String.join("", parts));
  }

  private String toString(DataBuffer dataBuffer) {
    var bytes = new byte[dataBuffer.readableByteCount()];
    dataBuffer.read(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
