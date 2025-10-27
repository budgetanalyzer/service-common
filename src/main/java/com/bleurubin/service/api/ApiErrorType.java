package com.bleurubin.service.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error type categorization for API responses")
public enum ApiErrorType {
  @Schema(description = "Malformed request or bad syntax")
  INVALID_REQUEST,

  @Schema(description = "Field validation failed - check errors array")
  VALIDATION_ERROR,

  @Schema(description = "Requested resource does not exist")
  NOT_FOUND,

  @Schema(description = "Business rule violation - check code field")
  BUSINESS_ERROR,

  @Schema(description = "Downstream service unavailable")
  SERVICE_UNAVAILABLE,

  @Schema(description = "Unexpected server error (HTTP 500)")
  INTERNAL_ERROR
}
