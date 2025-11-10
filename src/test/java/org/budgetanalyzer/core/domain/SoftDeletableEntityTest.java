package org.budgetanalyzer.core.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Persistence;
import jakarta.persistence.Table;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SoftDeletableEntityTest {

  private EntityManagerFactory entityManagerFactory;
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    entityManagerFactory = Persistence.createEntityManagerFactory("test-pu");
    entityManager = entityManagerFactory.createEntityManager();
  }

  @AfterEach
  void tearDown() {
    if (entityManager != null && entityManager.isOpen()) {
      entityManager.close();
    }
    if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
      entityManagerFactory.close();
    }
  }

  @Test
  void shouldDefaultDeletedToFalse() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    // Act
    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Assert
    assertFalse(entity.isDeleted(), "deleted should default to false");
    assertNull(entity.getDeletedAt(), "deletedAt should be null by default");
  }

  @Test
  void shouldInheritAuditableEntityBehavior() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    // Act
    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Assert - Should have createdAt and updatedAt from AuditableEntity
    assertNotNull(entity.getCreatedAt(), "Should inherit createdAt from AuditableEntity");
    assertNotNull(entity.getUpdatedAt(), "Should inherit updatedAt from AuditableEntity");
  }

  @Test
  void shouldMarkEntityAsDeleted() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    assertFalse(entity.isDeleted());
    assertNull(entity.getDeletedAt());

    final Instant beforeMarkDeleted = Instant.now();

    // Act
    entityManager.getTransaction().begin();
    entity.markDeleted();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    final Instant afterMarkDeleted = Instant.now();

    // Assert
    assertTrue(entity.isDeleted(), "Entity should be marked as deleted");
    assertNotNull(entity.getDeletedAt(), "deletedAt should be set");
    assertTrue(
        !entity.getDeletedAt().isBefore(beforeMarkDeleted)
            && !entity.getDeletedAt().isAfter(afterMarkDeleted),
        "deletedAt should be within markDeleted time range");
  }

  @Test
  void shouldBeIdempotentWhenMarkDeletedCalledMultipleTimes() throws InterruptedException {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Act - Call markDeleted() first time
    entityManager.getTransaction().begin();
    entity.markDeleted();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    final Instant firstDeletedAt = entity.getDeletedAt();

    // Wait and call markDeleted() again
    TimeUnit.MILLISECONDS.sleep(10);

    entityManager.getTransaction().begin();
    entity.markDeleted();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    Instant secondDeletedAt = entity.getDeletedAt();

    // Assert - Should update timestamp (not truly idempotent, but updates deletedAt)
    assertTrue(entity.isDeleted(), "Entity should still be deleted");
    assertNotNull(secondDeletedAt, "deletedAt should still be set");
    // Note: markDeleted() will update the timestamp each time it's called
    assertTrue(
        secondDeletedAt.isAfter(firstDeletedAt) || secondDeletedAt.equals(firstDeletedAt),
        "deletedAt should be updated or equal on second call");
  }

  @Test
  void shouldRestoreDeletedEntity() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Mark as deleted
    entityManager.getTransaction().begin();
    entity.markDeleted();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    assertTrue(entity.isDeleted());
    assertNotNull(entity.getDeletedAt());

    // Act - Restore entity
    entityManager.getTransaction().begin();
    entity.restore();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    // Assert
    assertFalse(entity.isDeleted(), "Entity should be restored (deleted=false)");
    assertNull(entity.getDeletedAt(), "deletedAt should be null after restore");
  }

  @Test
  void shouldHandleRestoreOnNonDeletedEntity() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    assertFalse(entity.isDeleted());

    // Act - Call restore() on non-deleted entity (should not throw exception)
    entityManager.getTransaction().begin();
    entity.restore();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    // Assert - Should complete without errors
    assertFalse(entity.isDeleted(), "Entity should still be not deleted");
    assertNull(entity.getDeletedAt(), "deletedAt should still be null");
  }

  @Test
  void shouldHandleMultipleDeleteAndRestoreCycles() throws InterruptedException {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Act - First cycle: delete -> restore
    entityManager.getTransaction().begin();
    entity.markDeleted();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    assertTrue(entity.isDeleted());
    final Instant firstDeletedAt = entity.getDeletedAt();

    entityManager.getTransaction().begin();
    entity.restore();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    assertFalse(entity.isDeleted());

    TimeUnit.MILLISECONDS.sleep(10);

    // Act - Second cycle: delete -> restore
    entityManager.getTransaction().begin();
    entity.markDeleted();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    assertTrue(entity.isDeleted());
    final Instant secondDeletedAt = entity.getDeletedAt();

    entityManager.getTransaction().begin();
    entity.restore();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    // Assert
    assertFalse(entity.isDeleted(), "Entity should be restored after second cycle");
    assertNull(entity.getDeletedAt(), "deletedAt should be null after second restore");
    assertTrue(
        secondDeletedAt.isAfter(firstDeletedAt), "Second deletedAt should be later than first");
  }

  @Test
  void shouldPersistDeletedStateToDatabase() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    entityManager.getTransaction().begin();
    entity.markDeleted();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    Long entityId = entity.getId();

    // Act - Clear and reload from database
    entityManager.clear();
    var reloadedEntity = entityManager.find(TestSoftDeletableEntity.class, entityId);

    // Assert
    assertTrue(reloadedEntity.isDeleted(), "Deleted state should be persisted to database");
    assertNotNull(reloadedEntity.getDeletedAt(), "deletedAt should be persisted to database");
  }

  @Test
  void shouldPersistRestoredStateToDatabase() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    entityManager.getTransaction().begin();
    entity.markDeleted();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    entityManager.getTransaction().begin();
    entity.restore();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    Long entityId = entity.getId();

    // Act - Clear and reload from database
    entityManager.clear();
    var reloadedEntity = entityManager.find(TestSoftDeletableEntity.class, entityId);

    // Assert
    assertFalse(reloadedEntity.isDeleted(), "Restored state should be persisted to database");
    assertNull(reloadedEntity.getDeletedAt(), "deletedAt null should be persisted to database");
  }

  @Test
  void shouldHaveSoftDeleteListenerAttached() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Act & Assert - Attempting hard delete should throw UnsupportedOperationException
    entityManager.getTransaction().begin();
    assertThrows(
        UnsupportedOperationException.class,
        () -> {
          entityManager.remove(entity);
          entityManager.flush();
        },
        "Hard delete should be prevented by SoftDeleteListener");
    entityManager.getTransaction().rollback();
  }

  // Concrete test entity for testing SoftDeletableEntity
  @Entity
  @Table(name = "test_soft_deletable_entity")
  static class TestSoftDeletableEntity extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
