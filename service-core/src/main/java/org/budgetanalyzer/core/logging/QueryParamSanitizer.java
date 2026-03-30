package org.budgetanalyzer.core.logging;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Sanitizes query parameters by masking values of sensitive parameter names.
 *
 * <p>Splits on {@code &}, splits each segment on the first {@code =}, masks the value if the
 * parameter name matches a known sensitive name. Preserves parameter order and encoding. Malformed
 * segments (no {@code =}) are passed through unchanged.
 *
 * <p>Name matching follows the same normalization as {@link HttpBodyLogSanitizer}: strip
 * non-alphanumeric characters, lowercase, then compare against known sensitive names. So {@code
 * access_token}, {@code Access-Token}, and {@code accessToken} all match {@code accesstoken}.
 *
 * <p>Hardcoded defaults are always applied and cannot be removed. Configured additions are merged
 * on top.
 */
public final class QueryParamSanitizer {

  private static final String MASKED_VALUE = "***";

  private static final Set<String> DEFAULT_SENSITIVE_PARAMS =
      Set.of(
          "code",
          "state",
          "token",
          "accesstoken",
          "refreshtoken",
          "sessionid",
          "sid",
          "password",
          "secret",
          "credential");

  private final Set<String> sensitiveParams;

  /**
   * Creates a sanitizer with default sensitive parameters merged with additional ones.
   *
   * @param additionalSensitiveParams extra parameter names to treat as sensitive (normalized
   *     internally); may be null or empty
   */
  public QueryParamSanitizer(Collection<String> additionalSensitiveParams) {
    var merged = new HashSet<>(DEFAULT_SENSITIVE_PARAMS);
    if (additionalSensitiveParams != null) {
      for (var param : additionalSensitiveParams) {
        merged.add(normalizeParamName(param));
      }
    }
    this.sensitiveParams = Set.copyOf(merged);
  }

  /** Creates a sanitizer with only the default sensitive parameters. */
  public QueryParamSanitizer() {
    this(Set.of());
  }

  /**
   * Sanitizes a raw query string by masking values of sensitive parameters.
   *
   * @param queryString the raw query string (without leading {@code ?}), may be null
   * @return sanitized query string with sensitive values replaced by {@code ***}, or the original
   *     value if null or empty
   */
  public String sanitize(String queryString) {
    if (queryString == null || queryString.isEmpty()) {
      return queryString;
    }

    var parts = queryString.split("&", -1);
    var result = new StringBuilder();

    for (var index = 0; index < parts.length; index++) {
      if (index > 0) {
        result.append('&');
      }

      var part = parts[index];
      var equalsIndex = part.indexOf('=');
      if (equalsIndex < 0) {
        result.append(part);
        continue;
      }

      var rawName = part.substring(0, equalsIndex);
      var decodedName = decodeComponent(rawName);

      if (isSensitive(decodedName)) {
        result.append(rawName).append('=').append(MASKED_VALUE);
      } else {
        result.append(part);
      }
    }

    return result.toString();
  }

  private boolean isSensitive(String paramName) {
    return sensitiveParams.contains(normalizeParamName(paramName));
  }

  private static String normalizeParamName(String name) {
    if (name == null) {
      return "";
    }
    return name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
  }

  private static String decodeComponent(String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      return value;
    }
  }
}
