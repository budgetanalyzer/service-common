package org.budgetanalyzer.service.security;

import java.util.ArrayList;
import java.util.List;

final class ClaimsHeaderValidator {

  private static final int MAX_USER_ID_LENGTH = 128;
  private static final int MAX_LIST_HEADER_LENGTH = 4096;
  private static final int MAX_CLAIM_VALUE_LENGTH = 128;
  private static final int MAX_CLAIM_VALUE_COUNT = 64;

  private ClaimsHeaderValidator() {}

  static boolean hasAnyClaimsHeaders(String userId, String permissionsHeader, String rolesHeader) {
    return userId != null || permissionsHeader != null || rolesHeader != null;
  }

  static ValidatedClaimsHeaders validate(
      String userIdHeader, String permissionsHeader, String rolesHeader) {
    var userId = validateUserId(userIdHeader);
    var permissions =
        parseListHeader(ClaimsHeaderAuthenticationFilter.X_PERMISSIONS_HEADER, permissionsHeader);
    var roles = parseListHeader(ClaimsHeaderAuthenticationFilter.X_ROLES_HEADER, rolesHeader);
    return new ValidatedClaimsHeaders(userId, permissions, roles);
  }

  private static String validateUserId(String userIdHeader) {
    if (userIdHeader == null) {
      throw new ClaimsHeaderValidationException(
          ClaimsHeaderAuthenticationFilter.X_USER_ID_HEADER + " header is required");
    }

    var userId = userIdHeader.trim();
    if (userId.isEmpty()) {
      throw new ClaimsHeaderValidationException(
          ClaimsHeaderAuthenticationFilter.X_USER_ID_HEADER + " header must not be blank");
    }
    if (userId.length() > MAX_USER_ID_LENGTH) {
      throw new ClaimsHeaderValidationException(
          ClaimsHeaderAuthenticationFilter.X_USER_ID_HEADER + " header exceeds max length");
    }
    validateTokenValue(ClaimsHeaderAuthenticationFilter.X_USER_ID_HEADER, userId);
    return userId;
  }

  private static List<String> parseListHeader(String headerName, String headerValue) {
    if (headerValue == null) {
      return List.of();
    }
    if (headerValue.length() > MAX_LIST_HEADER_LENGTH) {
      throw new ClaimsHeaderValidationException(headerName + " header exceeds max length");
    }

    validateHeaderCharacters(headerName, headerValue);

    var parsedValues = new ArrayList<String>();
    for (var valuePart : headerValue.split(",", -1)) {
      var trimmedValue = valuePart.trim();
      if (trimmedValue.isEmpty()) {
        throw new ClaimsHeaderValidationException(headerName + " header contains an empty value");
      }
      if (trimmedValue.length() > MAX_CLAIM_VALUE_LENGTH) {
        throw new ClaimsHeaderValidationException(headerName + " value exceeds max length");
      }
      validateTokenValue(headerName, trimmedValue);
      parsedValues.add(trimmedValue);
    }

    if (parsedValues.size() > MAX_CLAIM_VALUE_COUNT) {
      throw new ClaimsHeaderValidationException(headerName + " header contains too many values");
    }

    return List.copyOf(parsedValues);
  }

  private static void validateHeaderCharacters(String headerName, String headerValue) {
    for (var index = 0; index < headerValue.length(); index++) {
      var character = headerValue.charAt(index);
      if (character < 0x20 || character > 0x7E) {
        throw new ClaimsHeaderValidationException(
            headerName + " header contains non-printable characters");
      }
    }
  }

  private static void validateTokenValue(String headerName, String value) {
    for (var index = 0; index < value.length(); index++) {
      var character = value.charAt(index);
      if (character < 0x21 || character > 0x7E || character == ',') {
        throw new ClaimsHeaderValidationException(
            headerName + " header contains unsupported characters");
      }
    }
  }
}
