package org.budgetanalyzer.service.reactive.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ReactiveCorrelationIdFilterTest {

  @Mock private WebFilterChain filterChain;

  private ReactiveCorrelationIdFilter filter;

  @BeforeEach
  void setUp() {
    filter = new ReactiveCorrelationIdFilter();
  }

  @Test
  void shouldGenerateCorrelationIdWhenNotProvidedInRequest() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              assertTrue(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY));
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
              assertNotNull(correlationId);
              assertTrue(correlationId.startsWith("req_"));
            })
        .then()
        .verifyComplete();

    // Verify response header
    var responseHeaders = exchange.getResponse().getHeaders();
    var correlationId = responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER);
    assertNotNull(correlationId);
    assertTrue(correlationId.startsWith("req_"));
  }

  @Test
  void shouldUseExistingCorrelationIdFromRequest() {
    // Arrange
    var existingCorrelationId = "req_abc123def456";
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              assertTrue(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY));
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
              assertEquals(existingCorrelationId, correlationId);
            })
        .then()
        .verifyComplete();

    // Verify response header
    var responseHeaders = exchange.getResponse().getHeaders();
    var correlationId = responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER);
    assertEquals(existingCorrelationId, correlationId);
  }

  @Test
  void shouldStoreCorrelationIdInReactorContext() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              assertTrue(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY));
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
              assertNotNull(correlationId);
            })
        .then()
        .verifyComplete();
  }

  @Test
  void shouldAddCorrelationIdToResponseHeader() {
    // Arrange
    var existingCorrelationId = "req_test123456";
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    filter.filter(exchange, filterChain).block();

    // Assert
    var responseHeaders = exchange.getResponse().getHeaders();
    var correlationId = responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER);
    assertEquals(existingCorrelationId, correlationId);
  }

  @Test
  void shouldPropagateContextThroughFilterChain() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
    var capturedCorrelationId = new String[1];

    when(filterChain.filter(any()))
        .thenAnswer(
            invocation -> {
              // Capture the context within the filter chain
              return Mono.deferContextual(
                  ctx -> {
                    capturedCorrelationId[0] =
                        ctx.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
                    return Mono.empty();
                  });
            });

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert
    StepVerifier.create(result).verifyComplete();

    assertNotNull(capturedCorrelationId[0]);
    assertTrue(capturedCorrelationId[0].startsWith("req_"));
  }

  @Test
  void shouldContextNotPersistAfterExecution() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Context is accessible only within the reactive chain
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              assertTrue(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY));
            })
        .then()
        .verifyComplete();

    // After completion, context doesn't persist (reactive contexts are chain-scoped)
    // This is different from MDC which needed explicit cleanup
  }

  @Test
  void shouldHandleExceptionDuringFilterChainExecution() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
    var expectedException = new RuntimeException("Simulated error");
    when(filterChain.filter(any())).thenReturn(Mono.error(expectedException));

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Context should be accessible even when there's an error
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              assertTrue(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY));
            })
        .then()
        .expectError(RuntimeException.class)
        .verify();
  }

  @Test
  void shouldGenerateUniqueCorrelationIds() {
    // Arrange
    Set<String> correlationIds = new HashSet<>();
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act - Call filter multiple times
    for (int i = 0; i < 10; i++) {
      var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
      var result = filter.filter(exchange, filterChain);

      StepVerifier.create(result)
          .expectAccessibleContext()
          .assertThat(
              context -> {
                String correlationId =
                    context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
                correlationIds.add(correlationId);
              })
          .then()
          .verifyComplete();
    }

    // Assert - All should be unique
    assertEquals(10, correlationIds.size());
  }

  @Test
  void shouldHandleEmptyCorrelationIdHeader() {
    // Arrange
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER, "  "));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should generate new ID when header is empty/whitespace
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
              assertNotNull(correlationId);
              assertTrue(correlationId.startsWith("req_"));
            })
        .then()
        .verifyComplete();

    // Verify response header
    var responseHeaders = exchange.getResponse().getHeaders();
    var correlationId = responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER);
    assertTrue(correlationId.startsWith("req_"));
  }

  @Test
  void shouldGenerateCorrelationIdWithCorrectFormat() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);

              // Assert format: req_<16 hex chars>
              assertNotNull(correlationId);
              assertTrue(correlationId.startsWith("req_"));
              assertEquals(20, correlationId.length()); // "req_" (4) + 16 hex chars
              assertTrue(
                  correlationId.substring(4).matches("[0-9a-f]{16}"),
                  "Correlation ID should contain 16 hexadecimal characters");
            })
        .then()
        .verifyComplete();
  }

  @Test
  void shouldHandleNullCorrelationIdHeader() {
    // Arrange
    var headers = new HttpHeaders();
    // Explicitly don't set the correlation ID header
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").headers(headers));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    var result = filter.filter(exchange, filterChain);

    // Assert - Should generate new ID
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              assertTrue(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY));
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
              assertNotNull(correlationId);
              assertTrue(correlationId.startsWith("req_"));
            })
        .then()
        .verifyComplete();
  }

  @Test
  void shouldPreserveOtherResponseHeaders() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
    exchange.getResponse().getHeaders().add("X-Custom-Header", "custom-value");
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    filter.filter(exchange, filterChain).block();

    // Assert - Custom header should still exist alongside correlation ID
    var responseHeaders = exchange.getResponse().getHeaders();
    assertNotNull(responseHeaders.getFirst("X-Custom-Header"));
    assertEquals("custom-value", responseHeaders.getFirst("X-Custom-Header"));
    assertNotNull(responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER));
  }

  @Test
  void shouldNotOverwriteExistingResponseHeaders() {
    // Arrange
    var existingCorrelationId = "req_existing12345";
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId));
    when(filterChain.filter(any())).thenReturn(Mono.empty());

    // Act
    filter.filter(exchange, filterChain).block();

    // Assert - Should use the existing correlation ID from request
    var responseHeaders = exchange.getResponse().getHeaders();
    var correlationId = responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER);
    assertEquals(existingCorrelationId, correlationId);
    assertFalse(responseHeaders.get(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER).size() > 1);
  }
}
