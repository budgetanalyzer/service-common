package com.bleurubin.core.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
@EntityListeners(SoftDeleteListener.class)
public abstract class SoftDeletableEntity extends AuditableEntity {

  @Column(name = "deleted", nullable = false)
  private boolean deleted = false;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public boolean isDeleted() {
    return deleted;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void markDeleted() {
    this.deleted = true;
    this.deletedAt = Instant.now();
  }

  public void restore() {
    this.deleted = false;
    this.deletedAt = null;
  }
}
