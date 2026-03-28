package org.budgetanalyzer.service.reactive.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Decorator that caches the response body for logging.
 *
 * <p>Unlike request bodies which are cached upfront, response bodies are captured as they are
 * written by the handler.
 */
public class CachedBodyServerHttpResponseDecorator extends ServerHttpResponseDecorator {

  private final ByteArrayOutputStream cachedPrefix;
  private final AtomicInteger totalBytesRead = new AtomicInteger();
  private final int maxSize;

  /**
   * Constructs a decorator that caches the response body.
   *
   * @param delegate the original response
   * @param maxSize maximum size to cache (bytes)
   */
  public CachedBodyServerHttpResponseDecorator(ServerHttpResponse delegate, int maxSize) {
    super(delegate);
    this.maxSize = Math.max(maxSize, 0);
    this.cachedPrefix = new ByteArrayOutputStream(this.maxSize);
  }

  @Override
  public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
    return super.writeWith(Flux.from(body).doOnNext(this::cacheChunk));
  }

  /**
   * Gets the cached response body.
   *
   * @return response body string (may be truncated if exceeds maxSize)
   */
  public String getCachedBody() {
    var body = new String(cachedPrefix.toByteArray(), getCharset());
    if (totalBytesRead.get() > maxSize) {
      return body + "... [TRUNCATED]";
    }
    return body;
  }

  /**
   * Gets the size of the cached response body in bytes.
   *
   * @return number of bytes cached
   */
  public int getCachedBodySize() {
    return cachedPrefix.size();
  }

  private void cacheChunk(DataBuffer dataBuffer) {
    var readableBytes = dataBuffer.readableByteCount();
    totalBytesRead.addAndGet(readableBytes);

    if (maxSize == 0 || cachedPrefix.size() >= maxSize) {
      return;
    }

    var bytesToCache = Math.min(readableBytes, maxSize - cachedPrefix.size());
    if (bytesToCache <= 0) {
      return;
    }

    var bytes = new byte[bytesToCache];
    try (var buffers = dataBuffer.readableByteBuffers()) {
      var offset = 0;
      while (buffers.hasNext() && offset < bytesToCache) {
        var buffer = buffers.next();
        var bytesFromBuffer = Math.min(buffer.remaining(), bytesToCache - offset);
        buffer.get(bytes, offset, bytesFromBuffer);
        offset += bytesFromBuffer;
      }
    }

    cachedPrefix.write(bytes, 0, bytes.length);
  }

  private Charset getCharset() {
    var contentType = getDelegate().getHeaders().getContentType();
    if (contentType != null && contentType.getCharset() != null) {
      return contentType.getCharset();
    }
    return StandardCharsets.UTF_8;
  }
}
