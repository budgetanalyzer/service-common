package com.bleurubin.service.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard API error response format")
public class ApiErrorResponse {

  @Schema(
      description = "Error type indicating the category and structure of the error",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example = "APPLICATION_ERROR")
  private ApiErrorType type;

  @Schema(
      description = "Human-readable message describing the error",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example = "CSV format: fake-bank not supported")
  private String message;

  @Schema(
      description =
          "Machine-readable error code for specific application errors (required for APPLICATION_ERROR type)",
      example = "CSV_PARSING_ERROR")
  private String code;

  @Schema(
      description = "List of field-level validation errors (populated for VALIDATION_ERROR type)")
  private List<FieldError> fieldErrors;

  // Private constructor for builder
  private ApiErrorResponse() {}

  // Getters
  public ApiErrorType getType() {
    return type;
  }

  public String getMessage() {
    return message;
  }

  public String getCode() {
    return code;
  }

  public List<FieldError> getFieldErrors() {
    return fieldErrors;
  }

  // Builder
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final ApiErrorResponse response = new ApiErrorResponse();

    public Builder type(ApiErrorType type) {
      response.type = type;
      return this;
    }

    public Builder message(String message) {
      response.message = message;
      return this;
    }

    public Builder code(String code) {
      response.code = code;
      return this;
    }

    public Builder fieldErrors(List<FieldError> fieldErrors) {
      response.fieldErrors = fieldErrors;
      return this;
    }

    public ApiErrorResponse build() {
      return response;
    }
  }
}
