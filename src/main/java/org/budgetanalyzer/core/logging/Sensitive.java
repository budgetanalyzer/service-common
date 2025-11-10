package org.budgetanalyzer.core.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a field as sensitive so it will be masked in logs. */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {

  /**
   * Character to use for masking (default: *).
   *
   * @return the mask character
   */
  char maskChar() default '*';

  /**
   * Number of characters to show (default: 0). Set to non-zero to facilitate debugging.
   *
   * @return the number of characters to show at the end
   */
  int showLast() default 0;
}
