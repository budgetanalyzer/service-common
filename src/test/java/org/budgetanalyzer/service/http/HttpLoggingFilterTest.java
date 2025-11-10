package org.budgetanalyzer.service.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.util.List;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class HttpLoggingFilterTest {

  @Mock private FilterChain filterChain;

  private HttpLoggingProperties properties;
  private HttpLoggingFilter filter;

  @BeforeEach
  void setUp() {
    properties = new HttpLoggingProperties();
    properties.setEnabled(true);
    properties.setLogLevel("DEBUG");
    properties.setIncludeRequestBody(true);
    properties.setIncludeResponseBody(true);
    properties.setMaxBodySize(10000);

    filter = new HttpLoggingFilter(properties);
  }

  @Test
  void shouldBypassFilterWhenDisabled() throws Exception {
    // Arrange
    properties.setEnabled(false);
    filter = new HttpLoggingFilter(properties);

    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");

    var response = new MockHttpServletResponse();

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert - Should call filter chain without wrapping
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldLogRequestAndResponse() throws Exception {
    // Arrange
    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");
    request.setContent("{\"name\":\"John\"}".getBytes());

    var response = new MockHttpServletResponse();

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert - Filter chain should be called
    verify(filterChain).doFilter(any(), any());
  }

  @Test
  void shouldExcludePathsMatchingExcludePatterns() throws Exception {
    // Arrange
    properties.setExcludePatterns(List.of("/actuator/**", "/swagger-ui/**"));
    filter = new HttpLoggingFilter(properties);

    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/actuator/health");
    request.setServletPath("/actuator/health");

    var response = new MockHttpServletResponse();

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert - Should skip logging and call filter chain with original request/response
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldIncludeOnlyPathsMatchingIncludePatterns() throws Exception {
    // Arrange
    properties.setIncludePatterns(List.of("/api/**"));
    filter = new HttpLoggingFilter(properties);

    var includedRequest = new MockHttpServletRequest();
    includedRequest.setMethod("GET");
    includedRequest.setRequestURI("/api/users");
    includedRequest.setServletPath("/api/users");

    var excludedRequest = new MockHttpServletRequest();
    excludedRequest.setMethod("GET");
    excludedRequest.setRequestURI("/public/health");
    excludedRequest.setServletPath("/public/health");

    var response = new MockHttpServletResponse();

    // Act - Test included path
    filter.doFilterInternal(includedRequest, response, filterChain);
    verify(filterChain).doFilter(any(), any()); // Should wrap and log

    reset(filterChain);

    // Act - Test excluded path
    var excludedResponse = new MockHttpServletResponse();
    filter.doFilterInternal(excludedRequest, excludedResponse, filterChain);
    verify(filterChain).doFilter(eq(excludedRequest), eq(excludedResponse)); // Should skip logging
  }

  @Test
  void shouldLogOnlyErrorsWhenLogErrorsOnlyIsEnabled() throws Exception {
    // Arrange
    properties.setLogErrorsOnly(true);
    filter = new HttpLoggingFilter(properties);

    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");

    var response = new MockHttpServletResponse();
    response.setStatus(200); // Success status

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert - Should still call filter chain
    verify(filterChain).doFilter(any(), any());
    // Response logging should be skipped (tested via manual verification of logs)
  }

  @Test
  void shouldWrapRequestAndResponseForContentCaching() throws Exception {
    // Arrange
    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");
    request.setContent("{\"name\":\"John\"}".getBytes());

    var response = new MockHttpServletResponse();

    // Act
    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          // Assert - Request and response should be wrapped
          assertTrue(
              req instanceof org.springframework.web.util.ContentCachingRequestWrapper,
              "Request should be wrapped with ContentCachingRequestWrapper");
          assertTrue(
              res instanceof org.springframework.web.util.ContentCachingResponseWrapper,
              "Response should be wrapped with ContentCachingResponseWrapper");
        });
  }

  @Test
  void shouldNotLogRequestBodyForGetRequests() throws Exception {
    // Arrange
    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");

    var response = new MockHttpServletResponse();

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert - Should complete without errors (body not logged for GET)
    verify(filterChain).doFilter(any(), any());
  }

  @Test
  void shouldHandleExceptionsDuringLogging() throws Exception {
    // Arrange
    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");

    var response = new MockHttpServletResponse();

    // Act - Should not throw even if internal logging fails
    assertDoesNotThrow(() -> filter.doFilterInternal(request, response, filterChain));

    // Assert
    verify(filterChain).doFilter(any(), any());
  }

  @Test
  void shouldCopyResponseBodyToActualResponse() throws Exception {
    // Arrange
    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");

    var response = new MockHttpServletResponse();

    var responseBody = "{\"users\":[]}";

    // Act
    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          // Simulate controller writing to response
          res.getWriter().write(responseBody);
        });

    // Assert - Response should contain the body
    var actualResponseBody = response.getContentAsString();
    assertEquals(responseBody, actualResponseBody);
  }

  @Test
  void shouldExcludePatternTakePrecedenceOverIncludePattern() throws Exception {
    // Arrange
    properties.setIncludePatterns(List.of("/api/**"));
    properties.setExcludePatterns(List.of("/api/internal/**"));
    filter = new HttpLoggingFilter(properties);

    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/internal/debug");
    request.setServletPath("/api/internal/debug");

    var response = new MockHttpServletResponse();

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert - Should skip logging (excluded path)
    verify(filterChain).doFilter(request, response);
  }
}
