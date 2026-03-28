package org.budgetanalyzer.core.logging;

/**
 * Resolves inbound correlation IDs to a safe value for logs and response headers.
 *
 * <p>Valid inbound values are trimmed, length-bounded, and limited to a conservative token
 * character set. Malformed values are discarded and replaced with a generated correlation ID so
 * they are never reflected back to callers or written into logs.
 */
public final class CorrelationIdResolver {

  private static final int MAX_CORRELATION_ID_LENGTH = 128;

  private CorrelationIdResolver() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Resolves the correlation ID to a safe value.
   *
   * <p>If the provided value is absent or malformed, a new generated correlation ID is returned.
   *
   * @param correlationIdHeader inbound correlation ID header value
   * @return normalized inbound correlation ID or a newly generated value
   */
  public static String resolveOrGenerate(String correlationIdHeader) {
    var normalizedCorrelationId = normalize(correlationIdHeader);
    if (normalizedCorrelationId != null) {
      return normalizedCorrelationId;
    }
    return CorrelationIdGenerator.generate();
  }

  private static String normalize(String correlationIdHeader) {
    if (correlationIdHeader == null) {
      return null;
    }

    var trimmedCorrelationId = correlationIdHeader.trim();
    if (trimmedCorrelationId.isEmpty()
        || trimmedCorrelationId.length() > MAX_CORRELATION_ID_LENGTH) {
      return null;
    }

    for (var index = 0; index < trimmedCorrelationId.length(); index++) {
      if (!isSafeCharacter(trimmedCorrelationId.charAt(index))) {
        return null;
      }
    }

    return trimmedCorrelationId;
  }

  private static boolean isSafeCharacter(char character) {
    return (character >= 'a' && character <= 'z')
        || (character >= 'A' && character <= 'Z')
        || (character >= '0' && character <= '9')
        || character == '-'
        || character == '_'
        || character == '.'
        || character == ':';
  }
}
