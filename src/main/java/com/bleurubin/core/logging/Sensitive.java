package com.bleurubin.core.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a field as sensitive so it will be masked in logs */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {
  /** Character to use for masking (default: *) */
  char maskChar() default '*';

  /** Number of characters to show (default: 0). Set to 0 to completely mask the value. */
  int showLast() default 0;
}
