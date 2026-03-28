package org.budgetanalyzer.service.reactive.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;

import reactor.core.publisher.Flux;

/**
 * Decorator that captures a bounded request-body prefix for logging while allowing downstream
 * handlers to still read the full stream.
 *
 * <p>Reactive request bodies are single-consumption streams. This decorator avoids eagerly joining
 * the full body in memory; instead it observes the bytes as downstream code consumes them and
 * stores only a bounded prefix for later logging.
 */
public class CachedBodyServerHttpRequestDecorator extends ServerHttpRequestDecorator {

  private final Flux<DataBuffer> cachedBody;
  private final ByteArrayOutputStream cachedPrefix;
  private final AtomicInteger totalBytesRead = new AtomicInteger();
  private final int maxBodySize;

  /**
   * Constructs a decorator that captures up to {@code maxBodySize} bytes of the request body for
   * logging.
   *
   * @param delegate the original request
   * @param maxBodySize maximum number of bytes to cache for logging
   */
  public CachedBodyServerHttpRequestDecorator(ServerHttpRequest delegate, int maxBodySize) {
    super(delegate);
    this.maxBodySize = Math.max(maxBodySize, 0);
    this.cachedPrefix = new ByteArrayOutputStream(this.maxBodySize);
    this.cachedBody = super.getBody().doOnNext(this::cacheChunk);
  }

  @Override
  public Flux<DataBuffer> getBody() {
    return cachedBody;
  }

  /**
   * Reads the captured request-body prefix as a string.
   *
   * <p>The returned content only includes bytes observed while the downstream handler consumed the
   * request body. When the body exceeds the configured limit, the output is truncated with a suffix
   * describing the omitted byte count.
   *
   * @return cached body string
   */
  public String getCachedBodyAsString() {
    var body = new String(cachedPrefix.toByteArray(), getCharset());

    if (totalBytesRead.get() > maxBodySize) {
      var truncated = totalBytesRead.get() - maxBodySize;
      return body + "... [TRUNCATED - " + truncated + " bytes omitted]";
    }

    return body;
  }

  private void cacheChunk(DataBuffer dataBuffer) {
    var readableBytes = dataBuffer.readableByteCount();
    totalBytesRead.addAndGet(readableBytes);

    if (maxBodySize == 0 || cachedPrefix.size() >= maxBodySize) {
      return;
    }

    var bytesToCache = Math.min(readableBytes, maxBodySize - cachedPrefix.size());
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

  /**
   * Gets the charset from the request content type, or returns UTF-8 as default.
   *
   * @return charset
   */
  private Charset getCharset() {
    var contentType = getDelegate().getHeaders().getContentType();
    if (contentType != null && contentType.getCharset() != null) {
      return contentType.getCharset();
    }
    return StandardCharsets.UTF_8;
  }
}
