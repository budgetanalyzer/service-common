package com.bleurubin.service.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@ExtendWith(MockitoExtension.class)
class ContentLoggingUtilTest {

  @Mock private HttpServletRequest request;

  @Mock private HttpServletResponse response;

  @Mock private ContentCachingRequestWrapper requestWrapper;

  @Mock private ContentCachingResponseWrapper responseWrapper;

  private HttpLoggingProperties properties;

  @BeforeEach
  void setUp() {
    properties = new HttpLoggingProperties();
    properties.setEnabled(true);
    properties.setIncludeRequestHeaders(true);
    properties.setIncludeResponseHeaders(true);
    properties.setIncludeQueryParams(true);
    properties.setIncludeClientIp(true);
  }

  @Test
  void shouldExtractBasicRequestDetails() {
    // Arrange
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/users");
    when(request.getQueryString()).thenReturn("page=1&size=10");
    when(request.getRemoteAddr()).thenReturn("192.168.1.1");
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

    // Act
    var details = ContentLoggingUtil.extractRequestDetails(request, properties);

    // Assert
    assertEquals("GET", details.get("method"));
    assertEquals("/api/users", details.get("uri"));
    assertEquals("page=1&size=10", details.get("queryString"));
    assertEquals("192.168.1.1", details.get("clientIp"));
  }

  @Test
  void shouldMaskSensitiveRequestHeaders() {
    // Arrange
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/api/login");
    when(request.getQueryString()).thenReturn(null);
    lenient().when(request.getRemoteAddr()).thenReturn("192.168.1.1");
    when(request.getHeaderNames())
        .thenReturn(Collections.enumeration(List.of("Authorization", "Content-Type", "X-API-Key")));
    lenient().when(request.getHeader(anyString())).thenReturn(null); // Default for IP headers
    when(request.getHeader("Authorization")).thenReturn("Bearer secret-token");
    when(request.getHeader("Content-Type")).thenReturn("application/json");
    when(request.getHeader("X-API-Key")).thenReturn("my-secret-key");

    // Act
    var details = ContentLoggingUtil.extractRequestDetails(request, properties);

    // Assert
    @SuppressWarnings("unchecked")
    var headers = (Map<String, String>) details.get("headers");
    assertEquals("********", headers.get("Authorization"));
    assertEquals("application/json", headers.get("Content-Type"));
    assertEquals("********", headers.get("X-API-Key"));
  }

  @Test
  void shouldExtractClientIpFromXForwardedForHeader() {
    // Arrange
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/test");
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 198.51.100.1");
    lenient().when(request.getRemoteAddr()).thenReturn("192.168.1.1");

    // Act
    var details = ContentLoggingUtil.extractRequestDetails(request, properties);

    // Assert - Should use first IP from X-Forwarded-For
    assertEquals("203.0.113.1", details.get("clientIp"));
  }

  @Test
  void shouldExtractBasicResponseDetails() {
    // Arrange
    when(response.getStatus()).thenReturn(200);
    when(response.getHeaderNames()).thenReturn(Collections.emptyList());

    // Act
    var details = ContentLoggingUtil.extractResponseDetails(response, properties);

    // Assert
    assertEquals(200, details.get("status"));
  }

  @Test
  void shouldMaskSensitiveResponseHeaders() {
    // Arrange
    when(response.getStatus()).thenReturn(200);
    when(response.getHeaderNames()).thenReturn(List.of("Set-Cookie", "Content-Type"));
    when(response.getHeader("Set-Cookie")).thenReturn("session=abc123; HttpOnly");
    when(response.getHeader("Content-Type")).thenReturn("application/json");

    // Act
    var details = ContentLoggingUtil.extractResponseDetails(response, properties);

    // Assert
    @SuppressWarnings("unchecked")
    var headers = (Map<String, String>) details.get("headers");
    assertEquals("********", headers.get("Set-Cookie"));
    assertEquals("application/json", headers.get("Content-Type"));
  }

  @Test
  void shouldExtractRequestBodyWithinSizeLimit() {
    // Arrange
    var requestBody = "{\"username\":\"john\",\"password\":\"secret\"}";
    var contentBytes = requestBody.getBytes();

    when(requestWrapper.getContentAsByteArray()).thenReturn(contentBytes);
    when(requestWrapper.getCharacterEncoding()).thenReturn("UTF-8");

    // Act
    var extractedBody = ContentLoggingUtil.extractRequestBody(requestWrapper, 1000);

    // Assert
    assertEquals(requestBody, extractedBody);
  }

  @Test
  void shouldTruncateRequestBodyExceedingSizeLimit() {
    // Arrange
    var requestBody = "A".repeat(100);
    var contentBytes = requestBody.getBytes();

    when(requestWrapper.getContentAsByteArray()).thenReturn(contentBytes);
    when(requestWrapper.getCharacterEncoding()).thenReturn("UTF-8");

    // Act - Limit to 50 bytes
    var extractedBody = ContentLoggingUtil.extractRequestBody(requestWrapper, 50);

    // Assert
    assertTrue(extractedBody.startsWith("A".repeat(50)));
    assertTrue(extractedBody.contains("TRUNCATED"));
    assertTrue(extractedBody.contains("50 bytes omitted"));
  }

  @Test
  void shouldReturnNullForEmptyRequestBody() {
    // Arrange
    when(requestWrapper.getContentAsByteArray()).thenReturn(new byte[0]);

    // Act
    var extractedBody = ContentLoggingUtil.extractRequestBody(requestWrapper, 1000);

    // Assert
    assertNull(extractedBody);
  }

  @Test
  void shouldExtractResponseBodyWithinSizeLimit() {
    // Arrange
    var responseBody = "{\"status\":\"success\",\"data\":{}}";
    var contentBytes = responseBody.getBytes();

    when(responseWrapper.getContentAsByteArray()).thenReturn(contentBytes);
    when(responseWrapper.getCharacterEncoding()).thenReturn("UTF-8");

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertEquals(responseBody, extractedBody);
  }

  @Test
  void shouldTruncateResponseBodyExceedingSizeLimit() {
    // Arrange
    var responseBody = "B".repeat(200);
    var contentBytes = responseBody.getBytes();

    when(responseWrapper.getContentAsByteArray()).thenReturn(contentBytes);
    when(responseWrapper.getCharacterEncoding()).thenReturn("UTF-8");

    // Act - Limit to 100 bytes
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 100);

    // Assert
    assertTrue(extractedBody.startsWith("B".repeat(100)));
    assertTrue(extractedBody.contains("TRUNCATED"));
    assertTrue(extractedBody.contains("100 bytes omitted"));
  }

  @Test
  void shouldFormatLogMessageWithDetails() {
    // Arrange
    Map<String, Object> details = Map.of("method", "POST", "uri", "/api/users", "status", 201);
    var body = "{\"name\":\"John Doe\"}";

    // Act
    var logMessage = ContentLoggingUtil.formatLogMessage("HTTP Request", details, body);

    // Assert
    assertTrue(logMessage.contains("HTTP Request"));
    assertTrue(logMessage.contains("POST"));
    assertTrue(logMessage.contains("/api/users"));
    assertTrue(logMessage.contains("Details:"));
    assertTrue(logMessage.contains("Body:"));
    assertTrue(logMessage.contains("John Doe"));
  }

  @Test
  void shouldFormatLogMessageWithoutBody() {
    // Arrange
    Map<String, Object> details = Map.of("method", "GET", "uri", "/api/users");

    // Act
    var logMessage = ContentLoggingUtil.formatLogMessage("HTTP Request", details, null);

    // Assert
    assertTrue(logMessage.contains("HTTP Request"));
    assertTrue(logMessage.contains("GET"));
    assertTrue(logMessage.contains("/api/users"));
    assertFalse(logMessage.contains("Body:"));
  }

  @Test
  void shouldNotIncludeQueryParamsWhenDisabled() {
    // Arrange
    properties.setIncludeQueryParams(false);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/users");
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

    // Act
    var details = ContentLoggingUtil.extractRequestDetails(request, properties);

    // Assert
    assertFalse(details.containsKey("queryString"));
  }

  @Test
  void shouldNotIncludeClientIpWhenDisabled() {
    // Arrange
    properties.setIncludeClientIp(false);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/users");

    // Act
    var details = ContentLoggingUtil.extractRequestDetails(request, properties);

    // Assert
    assertFalse(details.containsKey("clientIp"));
  }

  @Test
  void shouldNotIncludeHeadersWhenDisabled() {
    // Arrange
    properties.setIncludeRequestHeaders(false);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/users");

    // Act
    var details = ContentLoggingUtil.extractRequestDetails(request, properties);

    // Assert
    assertFalse(details.containsKey("headers"));
  }
}
