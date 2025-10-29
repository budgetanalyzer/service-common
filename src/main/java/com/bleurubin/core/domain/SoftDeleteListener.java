package com.bleurubin.core.domain;

import jakarta.persistence.PreRemove;

public class SoftDeleteListener {

  @PreRemove
  public void preRemove(SoftDeletableEntity entity) {
    throw new UnsupportedOperationException("Hard delete not allowed. Use markDeleted() instead.");
  }
}
