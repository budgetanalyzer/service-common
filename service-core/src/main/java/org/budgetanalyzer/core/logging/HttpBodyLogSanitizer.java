package org.budgetanalyzer.core.logging;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Sanitizes HTTP bodies before they are written to logs.
 *
 * <p>Supports redaction for structured text payloads and suppresses unsafe body types such as
 * multipart, compressed, and binary content.
 */
public final class HttpBodyLogSanitizer {

  private static final String MASKED_VALUE = "***MASKED***";

  private static final Set<String> COMPRESSED_ENCODINGS =
      Set.of("gzip", "deflate", "br", "compress");

  private static final Set<String> JSON_CONTENT_TYPES =
      Set.of("application/json", "application/problem+json", "application/x-ndjson");

  private static final Set<String> SENSITIVE_FIELD_NAMES =
      Set.of(
          "apikey",
          "accesstoken",
          "authtoken",
          "authorization",
          "clientsecret",
          "credential",
          "credentials",
          "idtoken",
          "password",
          "passwd",
          "privatekey",
          "pwd",
          "refreshtoken",
          "secret",
          "secretkey",
          "sessionid",
          "token");

  // Jackson 3 fails on trailing tokens by default, so multi-root bodies are rejected and omitted.
  private static final JsonMapper jsonMapper = JsonMapper.builder().build();

  private HttpBodyLogSanitizer() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Prepares a request or response body for safe logging.
   *
   * @param cachedPrefix cached body prefix, already bounded by the caller
   * @param totalBodySize total number of bytes observed in the body
   * @param contentType Content-Type header value; may be null
   * @param contentEncoding Content-Encoding header value; may be null
   * @param characterEncoding body character encoding; may be null to use UTF-8
   * @return sanitized body string, an omission placeholder, or null when no body content is
   *     available
   */
  public static String prepareBodyForLogging(
      byte[] cachedPrefix,
      int totalBodySize,
      String contentType,
      String contentEncoding,
      String characterEncoding) {
    if (totalBodySize <= 0) {
      return null;
    }

    if (isCompressed(contentEncoding)) {
      return "[compressed content omitted: " + contentEncoding + ", " + totalBodySize + " bytes]";
    }

    var normalizedContentType = normalizeContentType(contentType);
    if (isMultipartContentType(normalizedContentType)) {
      return "[multipart content omitted: "
          + normalizedContentType
          + ", "
          + totalBodySize
          + " bytes]";
    }

    if (isBinaryContentType(normalizedContentType)) {
      return "[binary content omitted: " + normalizedContentType + ", " + totalBodySize + " bytes]";
    }

    var decodedBody = decodeBody(cachedPrefix, characterEncoding);
    var truncated = totalBodySize > cachedPrefix.length;

    if (isStructuredPayload(normalizedContentType, decodedBody)) {
      if (truncated) {
        return "[structured body omitted: "
            + describeContentType(normalizedContentType)
            + ", "
            + totalBodySize
            + " bytes, truncated before safe sanitization]";
      }

      var sanitizedBody = sanitizeStructuredBody(decodedBody, normalizedContentType);
      if (sanitizedBody == null) {
        return "[structured body omitted: "
            + describeContentType(normalizedContentType)
            + ", unable to safely sanitize]";
      }
      return sanitizedBody;
    }

    if (decodedBody.isEmpty()) {
      return null;
    }

    if (truncated) {
      return decodedBody
          + "... [TRUNCATED - "
          + (totalBodySize - cachedPrefix.length)
          + " bytes omitted]";
    }

    return decodedBody;
  }

  private static String sanitizeStructuredBody(String body, String normalizedContentType) {
    if (looksLikeJsonContentType(normalizedContentType) || looksLikeJsonShape(body)) {
      return sanitizeJson(body);
    }

    if (isFormUrlEncoded(normalizedContentType)) {
      return sanitizeFormUrlEncoded(body);
    }

    return body;
  }

  private static String sanitizeJson(String body) {
    try {
      var rootNode = jsonMapper.readTree(body);
      sanitizeJsonNode(rootNode);
      return jsonMapper.writeValueAsString(rootNode);
    } catch (Exception exception) {
      return null;
    }
  }

  private static void sanitizeJsonNode(JsonNode jsonNode) {
    if (jsonNode instanceof ObjectNode objectNode) {
      for (var propertyName : objectNode.propertyNames()) {
        if (isSensitiveFieldName(propertyName)) {
          objectNode.put(propertyName, MASKED_VALUE);
        } else {
          sanitizeJsonNode(objectNode.get(propertyName));
        }
      }
      return;
    }

    if (jsonNode instanceof ArrayNode arrayNode) {
      for (var index = 0; index < arrayNode.size(); index++) {
        sanitizeJsonNode(arrayNode.get(index));
      }
    }
  }

  private static String sanitizeFormUrlEncoded(String body) {
    var sanitizedParts = new StringBuilder();
    var parts = body.split("&", -1);

    for (var index = 0; index < parts.length; index++) {
      if (index > 0) {
        sanitizedParts.append('&');
      }

      var part = parts[index];
      var separatorIndex = part.indexOf('=');
      if (separatorIndex < 0) {
        sanitizedParts.append(part);
        continue;
      }

      var rawKey = part.substring(0, separatorIndex);
      if (isSensitiveFieldName(decodeFormComponent(rawKey))) {
        sanitizedParts.append(rawKey).append('=').append(MASKED_VALUE);
      } else {
        sanitizedParts.append(part);
      }
    }

    return sanitizedParts.toString();
  }

  private static String decodeFormComponent(String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
    } catch (IllegalArgumentException | UnsupportedEncodingException exception) {
      return value;
    }
  }

  private static String decodeBody(byte[] cachedPrefix, String characterEncoding) {
    if (cachedPrefix.length == 0) {
      return "";
    }

    try {
      return new String(
          cachedPrefix,
          characterEncoding != null ? characterEncoding : StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException exception) {
      return "[unable to read body: " + exception.getMessage() + "]";
    }
  }

  private static boolean isCompressed(String contentEncoding) {
    if (contentEncoding == null || contentEncoding.isBlank()) {
      return false;
    }

    var encodings = contentEncoding.toLowerCase(Locale.ROOT).split(",");
    for (var encoding : encodings) {
      if (COMPRESSED_ENCODINGS.contains(encoding.trim())) {
        return true;
      }
    }

    return false;
  }

  private static boolean isMultipartContentType(String normalizedContentType) {
    return normalizedContentType != null && normalizedContentType.startsWith("multipart/");
  }

  private static boolean isBinaryContentType(String normalizedContentType) {
    if (normalizedContentType == null || normalizedContentType.isBlank()) {
      return false;
    }

    if (normalizedContentType.startsWith("text/")) {
      return false;
    }

    if (looksLikeJsonContentType(normalizedContentType)
        || normalizedContentType.endsWith("+xml")
        || "application/xml".equals(normalizedContentType)
        || "text/xml".equals(normalizedContentType)
        || "application/javascript".equals(normalizedContentType)
        || "application/graphql".equals(normalizedContentType)
        || "application/graphql-response+json".equals(normalizedContentType)
        || "application/x-www-form-urlencoded".equals(normalizedContentType)) {
      return false;
    }

    return normalizedContentType.startsWith("application/")
        || normalizedContentType.startsWith("image/")
        || normalizedContentType.startsWith("audio/")
        || normalizedContentType.startsWith("video/")
        || normalizedContentType.startsWith("font/");
  }

  private static boolean isStructuredPayload(String normalizedContentType, String body) {
    return isFormUrlEncoded(normalizedContentType)
        || looksLikeJsonContentType(normalizedContentType)
        || looksLikeJsonShape(body);
  }

  private static boolean looksLikeJsonContentType(String normalizedContentType) {
    if (normalizedContentType == null || normalizedContentType.isBlank()) {
      return false;
    }

    return JSON_CONTENT_TYPES.contains(normalizedContentType)
        || normalizedContentType.endsWith("+json");
  }

  private static boolean isFormUrlEncoded(String normalizedContentType) {
    return "application/x-www-form-urlencoded".equals(normalizedContentType);
  }

  private static boolean looksLikeJsonShape(String body) {
    if (body == null) {
      return false;
    }

    var trimmedBody = body.trim();
    return trimmedBody.startsWith("{") || trimmedBody.startsWith("[");
  }

  private static String normalizeContentType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return null;
    }

    var separatorIndex = contentType.indexOf(';');
    var mediaType = separatorIndex >= 0 ? contentType.substring(0, separatorIndex) : contentType;
    return mediaType.trim().toLowerCase(Locale.ROOT);
  }

  private static String describeContentType(String normalizedContentType) {
    return normalizedContentType != null ? normalizedContentType : "unknown";
  }

  private static boolean isSensitiveFieldName(String fieldName) {
    var normalizedFieldName =
        fieldName == null ? "" : fieldName.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    return SENSITIVE_FIELD_NAMES.contains(normalizedFieldName);
  }
}
