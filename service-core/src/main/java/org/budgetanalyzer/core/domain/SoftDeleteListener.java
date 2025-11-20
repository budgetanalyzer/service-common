package org.budgetanalyzer.core.domain;

import jakarta.persistence.PreRemove;

/**
 * JPA lifecycle listener that prevents hard deletion of soft-deletable entities.
 *
 * <p>This listener intercepts delete operations on entities extending {@link SoftDeletableEntity}
 * and throws an exception to prevent accidental hard deletes. This ensures data retention
 * requirements are met and prevents referential integrity violations.
 *
 * <p>When {@code repository.delete(entity)} is called on a soft-deletable entity, this listener
 * triggers and throws {@link UnsupportedOperationException}. Instead, use {@link
 * SoftDeletableEntity#markDeleted(String)} to properly soft-delete entities.
 *
 * <p>This listener is automatically applied to all entities extending {@link SoftDeletableEntity}
 * via the {@code @EntityListeners} annotation.
 */
public class SoftDeleteListener {

  /**
   * JPA lifecycle callback that prevents hard deletion of soft-deletable entities.
   *
   * <p>This method is automatically invoked by JPA before removing an entity from the database. It
   * always throws an exception to prevent hard deletes.
   *
   * @param entity the entity about to be deleted
   * @throws UnsupportedOperationException always thrown to prevent hard deletion
   */
  @PreRemove
  public void preRemove(SoftDeletableEntity entity) {
    throw new UnsupportedOperationException(
        "Hard delete not allowed for " + entity.getClass() + ". Use markDeleted(deletedBy) instead.");
  }
}
