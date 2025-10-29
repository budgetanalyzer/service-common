package com.bleurubin.core.domain;

import java.time.Instant;

public interface SoftDeletable {

  SoftDeleteInfo getSoftDelete();

  default void markDeleted() {
    getSoftDelete().markDeleted();
  }

  default void restore() {
    getSoftDelete().restore();
  }

  default boolean isDeleted() {
    return getSoftDelete().isDeleted();
  }

  default Instant getDeletedAt() {
    return getSoftDelete().getDeletedAt();
  }
}
