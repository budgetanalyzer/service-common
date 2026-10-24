package org.budgetanalyzer.service.security;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.api.ApiErrorType;

final class ReactiveClaimsHeaderSecurityErrorResponseWriter {

  private ReactiveClaimsHeaderSecurityErrorResponseWriter() {}

  static Mono<Void> writeUnauthorized(ServerWebExchange exchange, JsonMapper jsonMapper) {
    return writeReactiveResponse(exchange, HttpStatus.UNAUTHORIZED, jsonMapper, unauthorized());
  }

  static Mono<Void> writeForbidden(ServerWebExchange exchange, JsonMapper jsonMapper) {
    return writeReactiveResponse(exchange, HttpStatus.FORBIDDEN, jsonMapper, forbidden());
  }

  private static Mono<Void> writeReactiveResponse(
      ServerWebExchange exchange,
      HttpStatus httpStatus,
      JsonMapper jsonMapper,
      ApiErrorResponse apiErrorResponse) {
    var response = exchange.getResponse();
    response.setStatusCode(httpStatus);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    byte[] body;
    try {
      body = jsonMapper.writeValueAsBytes(apiErrorResponse);
    } catch (JacksonException exception) {
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
    return ApiErrorResponse.builder(ApiErrorType.UNAUTHORIZED, "Authentication required").build();
  }

  private static ApiErrorResponse forbidden() {
    return ApiErrorResponse.builder(
            ApiErrorType.FORBIDDEN, "You do not have permission to perform this action")
        .build();
  }
}
