package com.bleurubin.core.logging;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

/** Jackson module that masks fields annotated with @Sensitive. */
public class SensitiveDataModule extends SimpleModule {

  /**
   * Constructs a SensitiveDataModule with custom serializer for sensitive fields.
   *
   * <p>This module registers a {@link SensitiveDataSerializerModifier} that masks fields annotated
   * with {@link Sensitive} during JSON serialization.
   */
  public SensitiveDataModule() {
    super("SensitiveDataModule");
    setSerializerModifier(new SensitiveDataSerializerModifier());
  }

  private static class SensitiveDataSerializerModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(
        SerializationConfig config,
        BeanDescription beanDesc,
        List<BeanPropertyWriter> beanProperties) {

      List<BeanPropertyWriter> modifiedProperties = new ArrayList<>();

      for (BeanPropertyWriter writer : beanProperties) {
        Sensitive annotation = writer.getAnnotation(Sensitive.class);

        if (annotation != null) {
          modifiedProperties.add(new SensitiveBeanPropertyWriter(writer, annotation));
        } else {
          modifiedProperties.add(writer);
        }
      }

      return modifiedProperties;
    }
  }

  private static class SensitiveBeanPropertyWriter extends BeanPropertyWriter {

    private final Sensitive annotation;

    protected SensitiveBeanPropertyWriter(BeanPropertyWriter base, Sensitive annotation) {
      super(base);
      this.annotation = annotation;
    }

    @Override
    public void serializeAsField(
        Object bean, JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider prov)
        throws Exception {

      Object value = get(bean);

      if (value == null) {
        super.serializeAsField(bean, gen, prov);
        return;
      }

      String stringValue = value.toString();
      String maskedValue = maskValue(stringValue, annotation.maskChar(), annotation.showLast());

      gen.writeFieldName(_name);
      gen.writeString(maskedValue);
    }

    private String maskValue(String value, char maskChar, int showLast) {
      if (value == null || value.isEmpty()) {
        return value;
      }

      if (showLast == 0) {
        // Completely mask
        return String.valueOf(maskChar).repeat(8);
      }

      if (value.length() <= showLast) {
        // Value is too short, mask it all
        return String.valueOf(maskChar).repeat(value.length());
      }

      String masked = String.valueOf(maskChar).repeat(value.length() - showLast);
      String visible = value.substring(value.length() - showLast);
      return masked + visible;
    }
  }
}
