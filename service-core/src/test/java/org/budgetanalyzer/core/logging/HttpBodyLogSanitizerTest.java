package org.budgetanalyzer.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class HttpBodyLogSanitizerTest {

  @Test
  void shouldRedactSensitiveFieldsInJsonPayloads() {
    var body =
        """
        {"username":"john","password":"secret","nested":{"accessToken":"abc123"}}
        """;

    var result =
        HttpBodyLogSanitizer.prepareBodyForLogging(
            body.getBytes(StandardCharsets.UTF_8),
            body.getBytes(StandardCharsets.UTF_8).length,
            "application/json",
            null,
            StandardCharsets.UTF_8.name());

    assertThat(result).contains("\"username\":\"john\"");
    assertThat(result).contains("\"password\":\"***MASKED***\"");
    assertThat(result).contains("\"accessToken\":\"***MASKED***\"");
    assertThat(result).doesNotContain("secret");
    assertThat(result).doesNotContain("abc123");
  }

  @Test
  void shouldRedactSensitiveFieldsInFormPayloads() {
    var body = "username=john&password=secret&api_key=abc123";

    var result =
        HttpBodyLogSanitizer.prepareBodyForLogging(
            body.getBytes(StandardCharsets.UTF_8),
            body.getBytes(StandardCharsets.UTF_8).length,
            "application/x-www-form-urlencoded",
            null,
            StandardCharsets.UTF_8.name());

    assertThat(result).isEqualTo("username=john&password=***MASKED***&api_key=***MASKED***");
  }

  @Test
  void shouldOmitTruncatedStructuredPayloads() {
    var body = "{\"password\":\"secret\",\"other\":\"value\"}";
    var cachedPrefix = body.substring(0, 12).getBytes(StandardCharsets.UTF_8);

    var result =
        HttpBodyLogSanitizer.prepareBodyForLogging(
            cachedPrefix,
            body.getBytes(StandardCharsets.UTF_8).length,
            "application/json",
            null,
            StandardCharsets.UTF_8.name());

    assertThat(result)
        .isEqualTo(
            "[structured body omitted: application/json, 37 bytes, "
                + "truncated before safe sanitization]");
  }

  @Test
  void shouldOmitMultipartBodies() {
    var body = "------boundary";

    var result =
        HttpBodyLogSanitizer.prepareBodyForLogging(
            body.getBytes(StandardCharsets.UTF_8),
            body.getBytes(StandardCharsets.UTF_8).length,
            "multipart/form-data; boundary=boundary",
            null,
            StandardCharsets.UTF_8.name());

    assertThat(result).isEqualTo("[multipart content omitted: multipart/form-data, 14 bytes]");
  }

  @Test
  void shouldOmitBinaryBodies() {
    var body = new byte[] {0x01, 0x02, 0x03};

    var result =
        HttpBodyLogSanitizer.prepareBodyForLogging(
            body, body.length, "application/octet-stream", null, StandardCharsets.UTF_8.name());

    assertThat(result).isEqualTo("[binary content omitted: application/octet-stream, 3 bytes]");
  }

  @Test
  void shouldOmitCompressedBodies() {
    var body = "{\"password\":\"secret\"}".getBytes(StandardCharsets.UTF_8);

    var result =
        HttpBodyLogSanitizer.prepareBodyForLogging(
            body, body.length, "application/json", "gzip", StandardCharsets.UTF_8.name());

    assertThat(result).isEqualTo("[compressed content omitted: gzip, 21 bytes]");
  }

  @Test
  void shouldKeepPlainTextBodies() {
    var body = "plain-text-body";

    var result =
        HttpBodyLogSanitizer.prepareBodyForLogging(
            body.getBytes(StandardCharsets.UTF_8),
            body.getBytes(StandardCharsets.UTF_8).length,
            "text/plain",
            null,
            StandardCharsets.UTF_8.name());

    assertThat(result).isEqualTo("plain-text-body");
  }
}
