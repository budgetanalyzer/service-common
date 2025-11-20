package org.budgetanalyzer.core.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base entity class that provides automatic audit tracking for creation and updates.
 *
 * <p>Extend this class to automatically add audit fields to your entity:
 *
 * <ul>
 *   <li>{@code createdAt} - Timestamp when the entity was first persisted
 *   <li>{@code updatedAt} - Timestamp of the last modification
 *   <li>{@code createdBy} - User ID who created the entity (requires AuditorAware bean)
 *   <li>{@code updatedBy} - User ID who last modified the entity (requires AuditorAware bean)
 * </ul>
 *
 * <p>Timestamps are managed automatically via JPA lifecycle callbacks. User tracking fields are
 * populated by Spring Data JPA auditing when an {@code AuditorAware<String>} bean is configured.
 *
 * <p>Example usage:
 *
 * <pre>
 * &#64;Entity
 * public class Transaction extends AuditableEntity {
 *     &#64;Id
 *     &#64;GeneratedValue(strategy = GenerationType.IDENTITY)
 *     private Long id;
 *
 *     private BigDecimal amount;
 *     // ... other fields
 * }
 * </pre>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

  /** Timestamp when this entity was created. */
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Timestamp when this entity was last updated. */
  @Column(name = "updated_at")
  private Instant updatedAt;

  /** User ID who created this entity. */
  @CreatedBy
  @Column(name = "created_by", length = 50, updatable = false)
  private String createdBy;

  /** User ID who last modified this entity. */
  @LastModifiedBy
  @Column(name = "updated_by", length = 50)
  private String updatedBy;

  /**
   * JPA lifecycle callback that sets timestamps when the entity is first persisted.
   *
   * <p>This method is automatically called by JPA before inserting the entity into the database.
   * Both {@code createdAt} and {@code updatedAt} are set to the current time.
   */
  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  /**
   * JPA lifecycle callback that updates the timestamp when the entity is modified.
   *
   * <p>This method is automatically called by JPA before updating the entity in the database. Only
   * {@code updatedAt} is modified; {@code createdAt} remains unchanged.
   */
  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  /**
   * Gets the timestamp when this entity was created.
   *
   * @return the creation timestamp
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Gets the timestamp when this entity was last updated.
   *
   * @return the last update timestamp
   */
  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /**
   * Gets the user ID who created this entity.
   *
   * @return the user ID who created this entity, or null if not set
   */
  public String getCreatedBy() {
    return createdBy;
  }

  /**
   * Gets the user ID who last modified this entity.
   *
   * @return the user ID who last modified this entity, or null if not set
   */
  public String getUpdatedBy() {
    return updatedBy;
  }
}
