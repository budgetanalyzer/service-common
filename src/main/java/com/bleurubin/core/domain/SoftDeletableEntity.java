package com.bleurubin.core.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

/**
 * Base entity class that provides soft-delete functionality with automatic timestamp tracking.
 *
 * <p>Soft delete allows entities to be marked as deleted without physically removing them from the
 * database. This is useful for:
 *
 * <ul>
 *   <li>Data retention and audit requirements
 *   <li>Ability to restore accidentally deleted records
 *   <li>Maintaining referential integrity (avoiding foreign key violations)
 *   <li>Preserving historical data for reporting
 * </ul>
 *
 * <p>This class extends {@link AuditableEntity}, so entities also get {@code createdAt} and {@code
 * updatedAt} timestamps automatically.
 *
 * <p>The {@link SoftDeleteListener} prevents hard deletes by throwing an exception if {@code
 * repository.delete(entity)} is called. Instead, use {@link #markDeleted()} to soft-delete
 * entities.
 *
 * <p>Example usage:
 *
 * <pre>
 * &#64;Entity
 * public class Transaction extends SoftDeletableEntity {
 *     &#64;Id
 *     &#64;GeneratedValue(strategy = GenerationType.IDENTITY)
 *     private Long id;
 *     // ... other fields
 * }
 *
 * // In service layer
 * transaction.markDeleted();
 * repository.save(transaction);
 *
 * // In repository - use SoftDeleteOperations interface
 * public interface TransactionRepository extends JpaRepository&lt;Transaction, Long&gt;,
 *                                                SoftDeleteOperations&lt;Transaction, Long&gt; {
 * }
 *
 * // Query only active (non-deleted) records
 * var activeTransactions = repository.findAllActive();
 * var transaction = repository.findByIdActive(id);
 * </pre>
 */
@MappedSuperclass
@EntityListeners(SoftDeleteListener.class)
public abstract class SoftDeletableEntity extends AuditableEntity {

  /** Flag indicating whether this entity has been soft-deleted. */
  @Column(name = "deleted", nullable = false)
  private boolean deleted = false;

  /** Timestamp when this entity was soft-deleted, or null if not deleted. */
  @Column(name = "deleted_at")
  private Instant deletedAt;

  /**
   * Checks whether this entity has been soft-deleted.
   *
   * @return true if this entity is deleted, false otherwise
   */
  public boolean isDeleted() {
    return deleted;
  }

  /**
   * Gets the timestamp when this entity was soft-deleted.
   *
   * @return the deletion timestamp, or null if not deleted
   */
  public Instant getDeletedAt() {
    return deletedAt;
  }

  /**
   * Marks this entity as soft-deleted.
   *
   * <p>Sets the {@code deleted} flag to true and records the current timestamp in {@code
   * deletedAt}. The entity remains in the database but will be excluded from queries using {@link
   * com.bleurubin.core.repository.SoftDeleteOperations} methods.
   *
   * <p>After calling this method, persist the changes by saving the entity via the repository.
   */
  public void markDeleted() {
    this.deleted = true;
    this.deletedAt = Instant.now();
  }

  /**
   * Restores a previously soft-deleted entity.
   *
   * <p>Clears the {@code deleted} flag and {@code deletedAt} timestamp, making the entity active
   * again. After calling this method, persist the changes by saving the entity via the repository.
   */
  public void restore() {
    this.deleted = false;
    this.deletedAt = null;
  }
}
