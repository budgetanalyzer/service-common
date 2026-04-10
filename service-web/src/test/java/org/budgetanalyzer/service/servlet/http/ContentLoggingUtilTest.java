package org.budgetanalyzer.service.servlet.http;

import static org.assertj.core.api.Assertions.assertThat;
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

import org.budgetanalyzer.core.logging.QueryParamSanitizer;
import org.budgetanalyzer.service.config.HttpLoggingProperties;

@ExtendWith(MockitoExtension.class)
class ContentLoggingUtilTest {

  @Mock private HttpServletRequest request;

  @Mock private HttpServletResponse response;

  @Mock private ContentCachingRequestWrapper requestWrapper;

  @Mock private ContentCachingResponseWrapper responseWrapper;

  private HttpLoggingProperties httpLoggingProperties;
  private QueryParamSanitizer queryParamSanitizer;

  @BeforeEach
  void setUp() {
    httpLoggingProperties = new HttpLoggingProperties();
    httpLoggingProperties.setEnabled(true);
    httpLoggingProperties.setIncludeRequestHeaders(true);
    httpLoggingProperties.setIncludeResponseHeaders(true);
    httpLoggingProperties.setIncludeQueryParams(true);
    httpLoggingProperties.setIncludeClientIp(true);
    queryParamSanitizer = new QueryParamSanitizer(httpLoggingProperties.getSensitiveQueryParams());
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
    var details =
        ContentLoggingUtil.extractRequestDetails(
            request, httpLoggingProperties, queryParamSanitizer);

    // Assert
    assertThat(details.get("method")).isEqualTo("GET");
    assertThat(details.get("uri")).isEqualTo("/api/users");
    assertThat(details.get("queryString")).isEqualTo("page=1&size=10");
    assertThat(details.get("clientIp")).isEqualTo("192.168.1.1");
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
    var details =
        ContentLoggingUtil.extractRequestDetails(
            request, httpLoggingProperties, queryParamSanitizer);

    // Assert
    @SuppressWarnings("unchecked")
    var headers = (Map<String, String>) details.get("headers");
    assertThat(headers.get("Authorization")).isEqualTo("***MASKED***");
    assertThat(headers.get("Content-Type")).isEqualTo("application/json");
    assertThat(headers.get("X-API-Key")).isEqualTo("***MASKED***");
  }

  @Test
  void shouldExtractClientIpFromXforwardedForHeader() {
    // Arrange
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/test");
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 198.51.100.1");
    lenient().when(request.getRemoteAddr()).thenReturn("192.168.1.1");

    // Act
    var details =
        ContentLoggingUtil.extractRequestDetails(
            request, httpLoggingProperties, queryParamSanitizer);

    // Assert - Should use first IP from X-Forwarded-For
    assertThat(details.get("clientIp")).isEqualTo("203.0.113.1");
  }

  @Test
  void shouldExtractBasicResponseDetails() {
    // Arrange
    when(response.getStatus()).thenReturn(200);
    when(response.getHeaderNames()).thenReturn(Collections.emptyList());

    // Act
    var details = ContentLoggingUtil.extractResponseDetails(response, httpLoggingProperties);

    // Assert
    assertThat(details.get("status")).isEqualTo(200);
  }

  @Test
  void shouldMaskSensitiveResponseHeaders() {
    // Arrange
    when(response.getStatus()).thenReturn(200);
    when(response.getHeaderNames()).thenReturn(List.of("Set-Cookie", "Content-Type"));
    when(response.getHeader("Set-Cookie")).thenReturn("session=abc123; HttpOnly");
    when(response.getHeader("Content-Type")).thenReturn("application/json");

    // Act
    var details = ContentLoggingUtil.extractResponseDetails(response, httpLoggingProperties);

    // Assert
    @SuppressWarnings("unchecked")
    var headers = (Map<String, String>) details.get("headers");
    assertThat(headers.get("Set-Cookie")).isEqualTo("***MASKED***");
    assertThat(headers.get("Content-Type")).isEqualTo("application/json");
  }

  @Test
  void shouldExtractRequestBodyWithinSizeLimit() {
    // Arrange
    var requestBody = "{\"username\":\"john\",\"password\":\"secret\"}";
    var contentBytes = requestBody.getBytes();

    when(requestWrapper.getContentAsByteArray()).thenReturn(contentBytes);
    when(requestWrapper.getCharacterEncoding()).thenReturn("UTF-8");
    when(requestWrapper.getContentType()).thenReturn("application/json");

    // Act
    var extractedBody = ContentLoggingUtil.extractRequestBody(requestWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo("{\"username\":\"john\",\"password\":\"***MASKED***\"}");
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
    assertThat(extractedBody.startsWith("A".repeat(50))).isTrue();
    assertThat(extractedBody.contains("TRUNCATED")).isTrue();
    assertThat(extractedBody.contains("50 bytes omitted")).isTrue();
  }

  @Test
  void shouldReturnNullForEmptyRequestBody() {
    // Arrange
    when(requestWrapper.getContentAsByteArray()).thenReturn(new byte[0]);

    // Act
    var extractedBody = ContentLoggingUtil.extractRequestBody(requestWrapper, 1000);

    // Assert
    assertThat(extractedBody).isNull();
  }

  @Test
  void shouldExtractResponseBodyWithinSizeLimit() {
    // Arrange
    var responseBody = "{\"status\":\"success\",\"data\":{}}";
    var contentBytes = responseBody.getBytes();

    when(responseWrapper.getContentAsByteArray()).thenReturn(contentBytes);
    when(responseWrapper.getCharacterEncoding()).thenReturn("UTF-8");
    when(responseWrapper.getContentType()).thenReturn("application/json");

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo(responseBody);
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
    assertThat(extractedBody.startsWith("B".repeat(100))).isTrue();
    assertThat(extractedBody.contains("TRUNCATED")).isTrue();
    assertThat(extractedBody.contains("100 bytes omitted")).isTrue();
  }

  @Test
  void shouldFormatLogMessageWithDetails() {
    // Arrange
    Map<String, Object> details = Map.of("method", "POST", "uri", "/api/users", "status", 201);
    var body = "{\"name\":\"John Doe\"}";

    // Act
    var logMessage = ContentLoggingUtil.formatLogMessage("HTTP Request", details, body);

    // Assert
    assertThat(logMessage.contains("HTTP Request")).isTrue();
    assertThat(logMessage.contains("POST")).isTrue();
    assertThat(logMessage.contains("/api/users")).isTrue();
    assertThat(logMessage.contains("Details:")).isTrue();
    assertThat(logMessage.contains("Body:")).isTrue();
    assertThat(logMessage.contains("John Doe")).isTrue();
  }

  @Test
  void shouldFormatLogMessageWithoutBody() {
    // Arrange
    Map<String, Object> details = Map.of("method", "GET", "uri", "/api/users");

    // Act
    var logMessage = ContentLoggingUtil.formatLogMessage("HTTP Request", details, null);

    // Assert
    assertThat(logMessage.contains("HTTP Request")).isTrue();
    assertThat(logMessage.contains("GET")).isTrue();
    assertThat(logMessage.contains("/api/users")).isTrue();
    assertThat(logMessage.contains("Body:")).isFalse();
  }

  @Test
  void shouldNotIncludeQueryParamsWhenDisabled() {
    // Arrange
    httpLoggingProperties.setIncludeQueryParams(false);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/users");
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

    // Act
    var details =
        ContentLoggingUtil.extractRequestDetails(
            request, httpLoggingProperties, queryParamSanitizer);

    // Assert
    assertThat(details.containsKey("queryString")).isFalse();
  }

  @Test
  void shouldNotIncludeClientIpWhenDisabled() {
    // Arrange
    httpLoggingProperties.setIncludeClientIp(false);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/users");

    // Act
    var details =
        ContentLoggingUtil.extractRequestDetails(
            request, httpLoggingProperties, queryParamSanitizer);

    // Assert
    assertThat(details.containsKey("clientIp")).isFalse();
  }

  @Test
  void shouldNotIncludeHeadersWhenDisabled() {
    // Arrange
    httpLoggingProperties.setIncludeRequestHeaders(false);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/users");

    // Act
    var details =
        ContentLoggingUtil.extractRequestDetails(
            request, httpLoggingProperties, queryParamSanitizer);

    // Assert
    assertThat(details.containsKey("headers")).isFalse();
  }

  @Test
  void shouldReturnPlaceholderForGzipCompressedResponse() {
    // Arrange
    var compressedBytes = new byte[] {0x1f, (byte) 0x8b, 0x08, 0x00}; // gzip magic bytes

    when(responseWrapper.getContentAsByteArray()).thenReturn(compressedBytes);
    when(responseWrapper.getHeader("Content-Encoding")).thenReturn("gzip");

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo("[compressed content omitted: gzip, 4 bytes]");
  }

  @Test
  void shouldReturnPlaceholderForDeflateCompressedResponse() {
    // Arrange
    var compressedBytes = new byte[100];

    when(responseWrapper.getContentAsByteArray()).thenReturn(compressedBytes);
    when(responseWrapper.getHeader("Content-Encoding")).thenReturn("deflate");

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo("[compressed content omitted: deflate, 100 bytes]");
  }

  @Test
  void shouldReturnPlaceholderForBrotliCompressedResponse() {
    // Arrange
    var compressedBytes = new byte[250];

    when(responseWrapper.getContentAsByteArray()).thenReturn(compressedBytes);
    when(responseWrapper.getHeader("Content-Encoding")).thenReturn("br");

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo("[compressed content omitted: br, 250 bytes]");
  }

  @Test
  void shouldReturnPlaceholderForMultipleEncodings() {
    // Arrange
    var compressedBytes = new byte[500];

    when(responseWrapper.getContentAsByteArray()).thenReturn(compressedBytes);
    when(responseWrapper.getHeader("Content-Encoding")).thenReturn("gzip, deflate");

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo("[compressed content omitted: gzip, deflate, 500 bytes]");
  }

  @Test
  void shouldReturnNormalBodyWhenNotCompressed() {
    // Arrange
    var responseBody = "{\"data\":\"test\"}";
    var contentBytes = responseBody.getBytes();

    when(responseWrapper.getContentAsByteArray()).thenReturn(contentBytes);
    when(responseWrapper.getCharacterEncoding()).thenReturn("UTF-8");
    when(responseWrapper.getContentType()).thenReturn("application/json");
    when(responseWrapper.getHeader("Content-Encoding")).thenReturn(null);

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo(responseBody);
  }

  @Test
  void shouldReturnNormalBodyWhenContentEncodingIsIdentity() {
    // Arrange
    var responseBody = "{\"data\":\"test\"}";
    var contentBytes = responseBody.getBytes();

    when(responseWrapper.getContentAsByteArray()).thenReturn(contentBytes);
    when(responseWrapper.getCharacterEncoding()).thenReturn("UTF-8");
    when(responseWrapper.getContentType()).thenReturn("application/json");
    when(responseWrapper.getHeader("Content-Encoding")).thenReturn("identity");

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo(responseBody);
  }

  @Test
  void shouldReturnPlaceholderForMultipartRequestBody() {
    // Arrange
    var requestBody = "--boundary\r\ncontent";
    var contentBytes = requestBody.getBytes();

    when(requestWrapper.getContentAsByteArray()).thenReturn(contentBytes);
    when(requestWrapper.getCharacterEncoding()).thenReturn("UTF-8");
    when(requestWrapper.getContentType()).thenReturn("multipart/form-data; boundary=boundary");

    // Act
    var extractedBody = ContentLoggingUtil.extractRequestBody(requestWrapper, 1000);

    // Assert
    assertThat(extractedBody)
        .isEqualTo("[multipart content omitted: multipart/form-data, 19 bytes]");
  }

  @Test
  void shouldReturnPlaceholderForBinaryResponseBody() {
    // Arrange
    var contentBytes = new byte[] {0x01, 0x02, 0x03};

    when(responseWrapper.getContentAsByteArray()).thenReturn(contentBytes);
    when(responseWrapper.getContentType()).thenReturn("application/octet-stream");
    when(responseWrapper.getHeader("Content-Encoding")).thenReturn(null);

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody)
        .isEqualTo("[binary content omitted: application/octet-stream, 3 bytes]");
  }
}
