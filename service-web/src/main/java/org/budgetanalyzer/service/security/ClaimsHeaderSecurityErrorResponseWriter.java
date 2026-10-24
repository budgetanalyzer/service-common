package org.budgetanalyzer.service.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;

import tools.jackson.databind.json.JsonMapper;

import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.api.ApiErrorType;

final class ClaimsHeaderSecurityErrorResponseWriter {

  private ClaimsHeaderSecurityErrorResponseWriter() {}

  static void writeUnauthorized(HttpServletResponse response, JsonMapper jsonMapper)
      throws IOException {
    writeServletResponse(response, HttpServletResponse.SC_UNAUTHORIZED, jsonMapper, unauthorized());
  }

  static void writeForbidden(HttpServletResponse response, JsonMapper jsonMapper)
      throws IOException {
    writeServletResponse(response, HttpServletResponse.SC_FORBIDDEN, jsonMapper, forbidden());
  }

  private static void writeServletResponse(
      HttpServletResponse response,
      int statusCode,
      JsonMapper jsonMapper,
      ApiErrorResponse apiErrorResponse)
      throws IOException {
    response.setStatus(statusCode);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    jsonMapper.writeValue(response.getWriter(), apiErrorResponse);
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
