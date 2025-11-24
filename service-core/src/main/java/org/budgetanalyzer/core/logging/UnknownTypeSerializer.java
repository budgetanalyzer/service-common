package org.budgetanalyzer.core.logging;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Serializer that falls back to toString() for types Jackson cannot serialize.
 *
 * <p>This prevents serialization failures for unknown types like Spring's HttpMethod by converting
 * them to their string representation instead of failing.
 */
public class UnknownTypeSerializer extends StdSerializer<Object> {

  /** Creates a new UnknownTypeSerializer. */
  public UnknownTypeSerializer() {
    super(Object.class);
  }

  @Override
  public void serialize(Object value, JsonGenerator gen, SerializerProvider provider)
      throws IOException {
    gen.writeString(value.toString());
  }

  /**
   * BeanSerializerModifier that replaces empty bean serializers with toString() fallback.
   *
   * <p>When Jackson detects a type with no serializable properties (empty bean), this modifier
   * replaces the default serializer with one that uses toString().
   */
  public static class ToStringFallbackModifier extends BeanSerializerModifier {

    @Override
    public JsonSerializer<?> modifySerializer(
        SerializationConfig config, BeanDescription beanDesc, JsonSerializer<?> serializer) {

      // Check if this would be an empty bean (no properties to serialize)
      if (beanDesc.findProperties().isEmpty() && !isStandardType(beanDesc.getBeanClass())) {
        return new UnknownTypeSerializer();
      }

      return serializer;
    }

    private boolean isStandardType(Class<?> clazz) {
      // Don't override standard types that should serialize as empty
      return clazz.isPrimitive()
          || clazz == String.class
          || Number.class.isAssignableFrom(clazz)
          || clazz == Boolean.class
          || clazz.isArray()
          || Iterable.class.isAssignableFrom(clazz)
          || java.util.Map.class.isAssignableFrom(clazz);
    }
  }
}
