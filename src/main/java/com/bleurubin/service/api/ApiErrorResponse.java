package com.bleurubin.service.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard API error response format")
public class ApiErrorResponse {

  @Schema(
      description = "Error type indicating the category and structure of the error",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example = "BUSINESS_ERROR")
  private ApiErrorType type;

  @Schema(
      description = "Human-readable message describing the error",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example = "CSV format: unknown-bank not supported")
  private String message;

  @Schema(
      description =
          "Machine-readable error code for specific business errors (required for BUSINESS_ERROR type)",
      requiredMode = Schema.RequiredMode.NOT_REQUIRED,
      example = "CSV_FORMAT_NOT_SUPPORTED")
  private String code;

  @Schema(
      description = "List of field-level validation errors (populated for VALIDATION_ERROR type)",
      requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  private List<FieldError> errors;

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

  public List<FieldError> getErrors() {
    return errors;
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

    public Builder errors(List<FieldError> errors) {
      response.errors = errors;
      return this;
    }

    public ApiErrorResponse build() {
      return response;
    }
  }

  @Schema(description = "Field-level validation error details")
  public static class FieldError {
    @Schema(
        description = "Field that triggered the error",
        requiredMode = Schema.RequiredMode.REQUIRED,
        example = "email")
    private String field;

    @Schema(
        description = "Error message",
        requiredMode = Schema.RequiredMode.REQUIRED,
        example = "must be a valid email address")
    private String message;

    @Schema(
        description = "Value that caused the error",
        requiredMode = Schema.RequiredMode.REQUIRED,
        example = "invalid@email")
    private Object rejectedValue;

    public FieldError() {}

    public FieldError(String field, String message, Object rejectedValue) {
      this.field = field;
      this.message = message;
      this.rejectedValue = rejectedValue;
    }

    public String getField() {
      return field;
    }

    public String getMessage() {
      return message;
    }

    public Object getRejectedValue() {
      return rejectedValue;
    }

    public static FieldError of(String field, String message, Object rejectedValue) {
      return new FieldError(field, message, rejectedValue);
    }
  }
}
