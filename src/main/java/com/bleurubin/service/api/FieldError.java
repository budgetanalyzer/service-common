package com.bleurubin.service.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Field-level validation error details")
public class FieldError {

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

  private FieldError() {}

  private FieldError(String field, String message, Object rejectedValue) {
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
