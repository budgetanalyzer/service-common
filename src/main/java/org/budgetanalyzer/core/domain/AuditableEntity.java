package org.budgetanalyzer.core.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * Base entity class that provides automatic timestamp tracking for creation and updates.
 *
 * <p>Extend this class to automatically add {@code createdAt} and {@code updatedAt} timestamp
 * fields to your entity. These timestamps are managed automatically via JPA lifecycle callbacks:
 *
 * <ul>
 *   <li>{@code createdAt} - Set once when the entity is first persisted
 *   <li>{@code updatedAt} - Updated automatically on every modification
 * </ul>
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
public abstract class AuditableEntity {

  /** Timestamp when this entity was created. */
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Timestamp when this entity was last updated. */
  @Column(name = "updated_at")
  private Instant updatedAt;

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
}
