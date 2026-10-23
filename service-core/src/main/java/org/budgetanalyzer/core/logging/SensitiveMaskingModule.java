package org.budgetanalyzer.core.logging;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.NopAnnotationIntrospector;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Logging-only Jackson module that masks members annotated with {@link Sensitive}.
 *
 * <p>The module inserts an annotation introspector ahead of Jackson's default introspector, so
 * standard annotations keep working while masking takes precedence over any other serializer
 * declared on the same member. Only the value is replaced: property names, null handling, and
 * inclusion rules remain Jackson's responsibility.
 */
final class SensitiveMaskingModule extends SimpleModule {

  private static final int FULL_MASK_LENGTH = 8;

  SensitiveMaskingModule() {
    super("SensitiveMaskingModule");
  }

  @Override
  public void setupModule(SetupContext context) {
    super.setupModule(context);
    context.insertAnnotationIntrospector(new SensitiveAnnotationIntrospector());
  }

  private static String mask(String value, char maskChar, int showLast) {
    if (value.isEmpty()) {
      return value;
    }

    var maskString = String.valueOf(maskChar);
    if (showLast <= 0) {
      return maskString.repeat(FULL_MASK_LENGTH);
    }

    if (value.length() <= showLast) {
      return maskString.repeat(value.length());
    }

    return maskString.repeat(value.length() - showLast)
        + value.substring(value.length() - showLast);
  }

  private static final class SensitiveAnnotationIntrospector extends NopAnnotationIntrospector {

    @Override
    public Object findSerializer(MapperConfig<?> config, Annotated annotated) {
      var sensitive = annotated.getAnnotation(Sensitive.class);
      if (sensitive == null) {
        return null;
      }
      return new SensitiveValueSerializer(sensitive.maskChar(), sensitive.showLast());
    }
  }

  private static final class SensitiveValueSerializer extends StdSerializer<Object> {

    private final char maskChar;
    private final int showLast;

    private SensitiveValueSerializer(char maskChar, int showLast) {
      super(Object.class);
      this.maskChar = maskChar;
      this.showLast = showLast;
    }

    @Override
    public boolean isEmpty(SerializationContext context, Object value) {
      return value == null || value.toString().isEmpty();
    }

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializationContext context) {
      generator.writeString(mask(value.toString(), maskChar, showLast));
    }
  }
}
