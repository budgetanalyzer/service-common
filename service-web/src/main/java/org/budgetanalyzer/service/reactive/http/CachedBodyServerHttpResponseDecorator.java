package org.budgetanalyzer.service.reactive.http;

import java.nio.charset.StandardCharsets;

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

  private final StringBuilder cachedBody = new StringBuilder();
  private final int maxSize;

  /**
   * Constructs a decorator that caches the response body.
   *
   * @param delegate the original response
   * @param maxSize maximum size to cache (bytes)
   */
  public CachedBodyServerHttpResponseDecorator(ServerHttpResponse delegate, int maxSize) {
    super(delegate);
    this.maxSize = maxSize;
  }

  @Override
  public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
    return super.writeWith(
        Flux.from(body)
            .doOnNext(
                dataBuffer -> {
                  // Only cache if we haven't exceeded max size
                  if (cachedBody.length() < maxSize) {
                    var bytesToRead =
                        Math.min(dataBuffer.readableByteCount(), maxSize - cachedBody.length());
                    byte[] bytes = new byte[bytesToRead];
                    // Save current position, read bytes, then restore position
                    var savedPosition = dataBuffer.readPosition();
                    dataBuffer.read(bytes, 0, bytesToRead);
                    dataBuffer.readPosition(savedPosition);
                    cachedBody.append(new String(bytes, StandardCharsets.UTF_8));
                  }
                }));
  }

  /**
   * Gets the cached response body.
   *
   * @return response body string (may be truncated if exceeds maxSize)
   */
  public String getCachedBody() {
    var body = cachedBody.toString();
    if (cachedBody.length() >= maxSize) {
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
    return cachedBody.length();
  }
}
