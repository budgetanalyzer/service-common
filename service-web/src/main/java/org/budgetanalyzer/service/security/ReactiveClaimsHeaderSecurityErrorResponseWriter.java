package org.budgetanalyzer.service.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.api.ApiErrorType;

final class ReactiveClaimsHeaderSecurityErrorResponseWriter {

  private ReactiveClaimsHeaderSecurityErrorResponseWriter() {}

  static Mono<Void> writeUnauthorized(ServerWebExchange exchange, ObjectMapper objectMapper) {
    return writeReactiveResponse(exchange, HttpStatus.UNAUTHORIZED, objectMapper, unauthorized());
  }

  static Mono<Void> writeForbidden(ServerWebExchange exchange, ObjectMapper objectMapper) {
    return writeReactiveResponse(exchange, HttpStatus.FORBIDDEN, objectMapper, forbidden());
  }

  private static Mono<Void> writeReactiveResponse(
      ServerWebExchange exchange,
      HttpStatus httpStatus,
      ObjectMapper objectMapper,
      ApiErrorResponse apiErrorResponse) {
    var response = exchange.getResponse();
    response.setStatusCode(httpStatus);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    byte[] body;
    try {
      body = objectMapper.writeValueAsBytes(apiErrorResponse);
    } catch (IOException exception) {
      body =
          "{\"type\":\"INTERNAL_ERROR\",\"message\":\"An unexpected error occurred\"}"
              .getBytes(StandardCharsets.UTF_8);
      response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
      response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }

    var buffer = response.bufferFactory().wrap(body);
    return response.writeWith(Mono.just(buffer));
  }

  private static ApiErrorResponse unauthorized() {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.UNAUTHORIZED)
        .message("Authentication required")
        .build();
  }

  private static ApiErrorResponse forbidden() {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.FORBIDDEN)
        .message("You do not have permission to perform this action")
        .build();
  }
}
