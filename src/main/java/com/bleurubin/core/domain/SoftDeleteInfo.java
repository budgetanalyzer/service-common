package com.bleurubin.core.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class SoftDeleteInfo {

  @Column(name = "deleted", nullable = false)
  private boolean deleted = false;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public void markDeleted() {
    deleted = true;
    deletedAt = Instant.now();
  }

  public void restore() {
    deleted = false;
    deletedAt = null;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }
}
