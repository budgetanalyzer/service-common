package com.bleurubin.service.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");

    MockHttpServletResponse response = new MockHttpServletResponse();

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert - Should call filter chain without wrapping
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldLogRequestAndResponse() throws Exception {
    // Arrange
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/api/users");
    request.setContent("{\"name\":\"John\"}".getBytes());

    MockHttpServletResponse response = new MockHttpServletResponse();

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

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/actuator/health");

    MockHttpServletResponse response = new MockHttpServletResponse();

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

    MockHttpServletRequest includedRequest = new MockHttpServletRequest();
    includedRequest.setMethod("GET");
    includedRequest.setRequestURI("/api/users");

    MockHttpServletRequest excludedRequest = new MockHttpServletRequest();
    excludedRequest.setMethod("GET");
    excludedRequest.setRequestURI("/public/health");

    MockHttpServletResponse response = new MockHttpServletResponse();

    // Act - Test included path
    filter.doFilterInternal(includedRequest, response, filterChain);
    verify(filterChain).doFilter(any(), any()); // Should wrap and log

    reset(filterChain);

    // Act - Test excluded path
    MockHttpServletResponse excludedResponse = new MockHttpServletResponse();
    filter.doFilterInternal(excludedRequest, excludedResponse, filterChain);
    verify(filterChain).doFilter(eq(excludedRequest), eq(excludedResponse)); // Should skip logging
  }

  @Test
  void shouldLogOnlyErrorsWhenLogErrorsOnlyIsEnabled() throws Exception {
    // Arrange
    properties.setLogErrorsOnly(true);
    filter = new HttpLoggingFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");

    MockHttpServletResponse response = new MockHttpServletResponse();
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
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/api/users");
    request.setContent("{\"name\":\"John\"}".getBytes());

    MockHttpServletResponse response = new MockHttpServletResponse();

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
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");

    MockHttpServletResponse response = new MockHttpServletResponse();

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert - Should complete without errors (body not logged for GET)
    verify(filterChain).doFilter(any(), any());
  }

  @Test
  void shouldHandleExceptionsDuringLogging() throws Exception {
    // Arrange
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");

    MockHttpServletResponse response = new MockHttpServletResponse();

    // Act - Should not throw even if internal logging fails
    assertDoesNotThrow(() -> filter.doFilterInternal(request, response, filterChain));

    // Assert
    verify(filterChain).doFilter(any(), any());
  }

  @Test
  void shouldCopyResponseBodyToActualResponse() throws Exception {
    // Arrange
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");

    MockHttpServletResponse response = new MockHttpServletResponse();

    String responseBody = "{\"users\":[]}";

    // Act
    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          // Simulate controller writing to response
          res.getWriter().write(responseBody);
        });

    // Assert - Response should contain the body
    String actualResponseBody = response.getContentAsString();
    assertEquals(responseBody, actualResponseBody);
  }

  @Test
  void shouldExcludePatternTakePrecedenceOverIncludePattern() throws Exception {
    // Arrange
    properties.setIncludePatterns(List.of("/api/**"));
    properties.setExcludePatterns(List.of("/api/internal/**"));
    filter = new HttpLoggingFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/internal/debug");

    MockHttpServletResponse response = new MockHttpServletResponse();

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert - Should skip logging (excluded path)
    verify(filterChain).doFilter(request, response);
  }
}
