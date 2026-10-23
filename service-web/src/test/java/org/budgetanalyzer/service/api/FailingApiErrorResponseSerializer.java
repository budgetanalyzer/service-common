package org.budgetanalyzer.service.api;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/** Serializer that always fails, used to exercise error-response serialization failure paths. */
public final class FailingApiErrorResponseSerializer extends ValueSerializer<ApiErrorResponse> {

  /** Message carried by the serialization failure. */
  public static final String FAILURE_MESSAGE = "ApiErrorResponse serialization failed";

  /**
   * Builds a real mapper that fails whenever it serializes an {@link ApiErrorResponse}.
   *
   * @return mapper with the failing serializer registered
   */
  public static JsonMapper jsonMapper() {
    return JsonMapper.builder()
        .addModule(
            new SimpleModule()
                .addSerializer(ApiErrorResponse.class, new FailingApiErrorResponseSerializer()))
        .build();
  }

  @Override
  public void serialize(
      ApiErrorResponse value, JsonGenerator generator, SerializationContext context) {
    throw DatabindException.from(generator, FAILURE_MESSAGE);
  }
}
