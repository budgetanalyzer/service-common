package org.budgetanalyzer.service.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.api.ApiErrorType;

final class ClaimsHeaderSecurityErrorResponseWriter {

  private ClaimsHeaderSecurityErrorResponseWriter() {}

  static void writeUnauthorized(HttpServletResponse response, ObjectMapper objectMapper)
      throws IOException {
    writeServletResponse(
        response, HttpServletResponse.SC_UNAUTHORIZED, objectMapper, unauthorized());
  }

  static void writeForbidden(HttpServletResponse response, ObjectMapper objectMapper)
      throws IOException {
    writeServletResponse(response, HttpServletResponse.SC_FORBIDDEN, objectMapper, forbidden());
  }

  private static void writeServletResponse(
      HttpServletResponse response,
      int statusCode,
      ObjectMapper objectMapper,
      ApiErrorResponse apiErrorResponse)
      throws IOException {
    response.setStatus(statusCode);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), apiErrorResponse);
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
