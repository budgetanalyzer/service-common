package org.budgetanalyzer.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import org.budgetanalyzer.service.api.FailingApiErrorResponseSerializer;

/** Unit tests for {@link ReactiveClaimsHeaderSecurityErrorResponseWriter}. */
@DisplayName("ReactiveClaimsHeaderSecurityErrorResponseWriter Tests")
class ReactiveClaimsHeaderSecurityErrorResponseWriterTest {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @Test
  @DisplayName("Should write unauthorized ApiErrorResponse")
  void shouldWriteUnauthorizedApiErrorResponse() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"));

    StepVerifier.create(
            ReactiveClaimsHeaderSecurityErrorResponseWriter.writeUnauthorized(exchange, jsonMapper))
        .verifyComplete();

    var responseBody = jsonMapper.readTree(exchange.getResponse().getBodyAsString().block());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(responseBody.path("type").asString()).isEqualTo("UNAUTHORIZED");
    assertThat(responseBody.path("message").asString()).isEqualTo("Authentication required");
    assertThat(responseBody.has("code")).isFalse();
    assertThat(responseBody.has("fieldErrors")).isFalse();
  }

  @Test
  @DisplayName("Should write forbidden ApiErrorResponse")
  void shouldWriteForbiddenApiErrorResponse() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"));

    StepVerifier.create(
            ReactiveClaimsHeaderSecurityErrorResponseWriter.writeForbidden(exchange, jsonMapper))
        .verifyComplete();

    var responseBody = jsonMapper.readTree(exchange.getResponse().getBodyAsString().block());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(responseBody.path("type").asString()).isEqualTo("FORBIDDEN");
    assertThat(responseBody.path("message").asString())
        .isEqualTo("You do not have permission to perform this action");
  }

  @Test
  @DisplayName("Should write internal error fallback when serialization fails")
  void shouldWriteInternalErrorFallbackWhenSerializationFails() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"));

    StepVerifier.create(
            ReactiveClaimsHeaderSecurityErrorResponseWriter.writeUnauthorized(
                exchange, FailingApiErrorResponseSerializer.jsonMapper()))
        .verifyComplete();

    var responseBody = jsonMapper.readTree(exchange.getResponse().getBodyAsString().block());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(responseBody.path("type").asString()).isEqualTo("INTERNAL_ERROR");
    assertThat(responseBody.path("message").asString()).isEqualTo("An unexpected error occurred");
  }
}
