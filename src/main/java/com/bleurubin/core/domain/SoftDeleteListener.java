package com.bleurubin.core.domain;

import jakarta.persistence.PreRemove;

/**
 * Goal is to prevent application code from accidentally deleting entities that are supposed to use
 * soft delete
 */
public class SoftDeleteListener {

  @PreRemove
  public void preRemove(SoftDeletableEntity entity) {
    throw new UnsupportedOperationException(
        "Hard delete not allowed for " + entity.getClass() + ". Use markDeleted() instead.");
  }
}
