package org.budgetanalyzer.service.servlet.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import org.budgetanalyzer.core.logging.QueryParamSanitizer;
import org.budgetanalyzer.service.config.HttpLoggingProperties;

class ContentLoggingUtilTest {

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
    var request = new MockHttpServletRequest("GET", "/api/users");
    request.setQueryString("page=1&size=10");
    request.setRemoteAddr("192.168.1.1");

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
    var request = new MockHttpServletRequest("POST", "/api/login");
    request.setRemoteAddr("192.168.1.1");
    request.addHeader("Authorization", "Bearer secret-token");
    request.addHeader("Content-Type", "application/json");
    request.addHeader("X-API-Key", "my-secret-key");

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
  void shouldHandleRequestWithoutHeaderEnumeration() {
    httpLoggingProperties.setIncludeClientIp(false);
    var request = new HeaderlessMockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");

    var details =
        ContentLoggingUtil.extractRequestDetails(
            request, httpLoggingProperties, queryParamSanitizer);

    assertThat(details.get("headers")).isEqualTo(Map.of());
  }

  @Test
  void shouldUseTrustedRealIpHeaderForClientIp() {
    var request = new MockHttpServletRequest("GET", "/api/test");
    request.addHeader("X-Real-IP", "203.0.113.1");
    request.setRemoteAddr("192.168.1.1");

    var details =
        ContentLoggingUtil.extractRequestDetails(
            request, httpLoggingProperties, queryParamSanitizer);

    assertThat(details.get("clientIp")).isEqualTo("203.0.113.1");
  }

  @Test
  void shouldIgnoreForgedForwardedForHeader() {
    var request = new MockHttpServletRequest("GET", "/api/test");
    request.addHeader("X-Forwarded-For", "203.0.113.1, 198.51.100.1");
    request.setRemoteAddr("192.168.1.1");

    var details =
        ContentLoggingUtil.extractRequestDetails(
            request, httpLoggingProperties, queryParamSanitizer);

    assertThat(details.get("clientIp")).isEqualTo("192.168.1.1");
  }

  @Test
  void shouldExtractBasicResponseDetails() {
    var response = new MockHttpServletResponse();
    response.setStatus(200);

    // Act
    var details = ContentLoggingUtil.extractResponseDetails(response, httpLoggingProperties);

    // Assert
    assertThat(details.get("status")).isEqualTo(200);
  }

  @Test
  void shouldMaskSensitiveResponseHeaders() {
    var response = new MockHttpServletResponse();
    response.setStatus(200);
    response.addHeader("Set-Cookie", "session=abc123; HttpOnly");
    response.addHeader("Content-Type", "application/json");

    // Act
    var details = ContentLoggingUtil.extractResponseDetails(response, httpLoggingProperties);

    // Assert
    @SuppressWarnings("unchecked")
    var headers = (Map<String, String>) details.get("headers");
    assertThat(headers.get("Set-Cookie")).isEqualTo("***MASKED***");
    assertThat(headers.get("Content-Type")).isEqualTo("application/json");
  }

  @Test
  void shouldExtractRequestBodyWithinSizeLimit() throws IOException {
    var requestBody = "{\"username\":\"john\",\"password\":\"secret\"}";
    var requestWrapper = cachedRequest(requestBody, "application/json", null);

    // Act
    var extractedBody = ContentLoggingUtil.extractRequestBody(requestWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo("{\"username\":\"john\",\"password\":\"***MASKED***\"}");
  }

  @Test
  void shouldTruncateRequestBodyExceedingSizeLimit() throws IOException {
    var requestBody = "A".repeat(100);
    var requestWrapper = cachedRequest(requestBody, null, null);

    // Act - Limit to 50 bytes
    var extractedBody = ContentLoggingUtil.extractRequestBody(requestWrapper, 50);

    // Assert
    assertThat(extractedBody.startsWith("A".repeat(50))).isTrue();
    assertThat(extractedBody.contains("TRUNCATED")).isTrue();
    assertThat(extractedBody.contains("50 bytes omitted")).isTrue();
  }

  @Test
  void shouldReturnNullForEmptyRequestBody() {
    var requestWrapper = new ContentCachingRequestWrapper(new MockHttpServletRequest(), 1);

    // Act
    var extractedBody = ContentLoggingUtil.extractRequestBody(requestWrapper, 1000);

    // Assert
    assertThat(extractedBody).isNull();
  }

  @Test
  void shouldExtractResponseBodyWithinSizeLimit() throws IOException {
    var responseBody = "{\"status\":\"success\",\"data\":{}}";
    var responseWrapper =
        cachedResponse(responseBody.getBytes(StandardCharsets.UTF_8), "application/json", null);

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo(responseBody);
  }

  @Test
  void shouldTruncateResponseBodyExceedingSizeLimit() throws IOException {
    var responseBody = "B".repeat(200);
    var responseWrapper = cachedResponse(responseBody.getBytes(StandardCharsets.UTF_8), null, null);

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
    httpLoggingProperties.setIncludeQueryParams(false);
    var request = new MockHttpServletRequest("GET", "/api/users");

    // Act
    var details =
        ContentLoggingUtil.extractRequestDetails(
            request, httpLoggingProperties, queryParamSanitizer);

    // Assert
    assertThat(details.containsKey("queryString")).isFalse();
  }

  @Test
  void shouldNotIncludeClientIpWhenDisabled() {
    httpLoggingProperties.setIncludeClientIp(false);
    var request = new MockHttpServletRequest("GET", "/api/users");

    // Act
    var details =
        ContentLoggingUtil.extractRequestDetails(
            request, httpLoggingProperties, queryParamSanitizer);

    // Assert
    assertThat(details.containsKey("clientIp")).isFalse();
  }

  @Test
  void shouldNotIncludeHeadersWhenDisabled() {
    httpLoggingProperties.setIncludeRequestHeaders(false);
    var request = new MockHttpServletRequest("GET", "/api/users");

    // Act
    var details =
        ContentLoggingUtil.extractRequestDetails(
            request, httpLoggingProperties, queryParamSanitizer);

    // Assert
    assertThat(details.containsKey("headers")).isFalse();
  }

  @Test
  void shouldReturnPlaceholderForGzipCompressedResponse() throws IOException {
    var compressedBytes = new byte[] {0x1f, (byte) 0x8b, 0x08, 0x00}; // gzip magic bytes
    var responseWrapper = cachedResponse(compressedBytes, null, "gzip");

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo("[compressed content omitted: gzip, 4 bytes]");
  }

  @Test
  void shouldReturnPlaceholderForDeflateCompressedResponse() throws IOException {
    var compressedBytes = new byte[100];
    var responseWrapper = cachedResponse(compressedBytes, null, "deflate");

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo("[compressed content omitted: deflate, 100 bytes]");
  }

  @Test
  void shouldReturnPlaceholderForBrotliCompressedResponse() throws IOException {
    var compressedBytes = new byte[250];
    var responseWrapper = cachedResponse(compressedBytes, null, "br");

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo("[compressed content omitted: br, 250 bytes]");
  }

  @Test
  void shouldReturnPlaceholderForMultipleEncodings() throws IOException {
    var compressedBytes = new byte[500];
    var responseWrapper = cachedResponse(compressedBytes, null, "gzip, deflate");

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo("[compressed content omitted: gzip, deflate, 500 bytes]");
  }

  @Test
  void shouldReturnNormalBodyWhenNotCompressed() throws IOException {
    var responseBody = "{\"data\":\"test\"}";
    var responseWrapper =
        cachedResponse(responseBody.getBytes(StandardCharsets.UTF_8), "application/json", null);

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo(responseBody);
  }

  @Test
  void shouldReturnNormalBodyWhenContentEncodingIsIdentity() throws IOException {
    var responseBody = "{\"data\":\"test\"}";
    var responseWrapper =
        cachedResponse(
            responseBody.getBytes(StandardCharsets.UTF_8), "application/json", "identity");

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody).isEqualTo(responseBody);
  }

  @Test
  void shouldReturnPlaceholderForMultipartRequestBody() throws IOException {
    var requestBody = "--boundary\r\ncontent";
    var requestWrapper = cachedRequest(requestBody, "multipart/form-data; boundary=boundary", null);

    // Act
    var extractedBody = ContentLoggingUtil.extractRequestBody(requestWrapper, 1000);

    // Assert
    assertThat(extractedBody)
        .isEqualTo("[multipart content omitted: multipart/form-data, 19 bytes]");
  }

  @Test
  void shouldReturnPlaceholderForBinaryResponseBody() throws IOException {
    var contentBytes = new byte[] {0x01, 0x02, 0x03};
    var responseWrapper = cachedResponse(contentBytes, "application/octet-stream", null);

    // Act
    var extractedBody = ContentLoggingUtil.extractResponseBody(responseWrapper, 1000);

    // Assert
    assertThat(extractedBody)
        .isEqualTo("[binary content omitted: application/octet-stream, 3 bytes]");
  }

  private ContentCachingRequestWrapper cachedRequest(
      String body, String contentType, String contentEncoding) throws IOException {
    var request = new MockHttpServletRequest();
    request.setCharacterEncoding(StandardCharsets.UTF_8.name());
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    request.setContentType(contentType);
    if (contentEncoding != null) {
      request.addHeader("Content-Encoding", contentEncoding);
    }

    var requestWrapper = new ContentCachingRequestWrapper(request, request.getContentLength());
    StreamUtils.copyToByteArray(requestWrapper.getInputStream());
    return requestWrapper;
  }

  private ContentCachingResponseWrapper cachedResponse(
      byte[] body, String contentType, String contentEncoding) throws IOException {
    var response = new MockHttpServletResponse();
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(contentType);
    if (contentEncoding != null) {
      response.addHeader("Content-Encoding", contentEncoding);
    }

    var responseWrapper = new ContentCachingResponseWrapper(response);
    responseWrapper.getOutputStream().write(body);
    return responseWrapper;
  }

  private static final class HeaderlessMockHttpServletRequest extends MockHttpServletRequest {

    @Override
    public Enumeration<String> getHeaderNames() {
      return null;
    }
  }
}
