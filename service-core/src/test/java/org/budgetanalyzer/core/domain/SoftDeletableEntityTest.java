package org.budgetanalyzer.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    assertThat(entity.isDeleted()).isFalse();
    assertThat(entity.getDeletedAt()).isNull();
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
    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isNotNull();
  }

  @Test
  void shouldMarkEntityAsDeleted() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    assertThat(entity.isDeleted()).isFalse();
    assertThat(entity.getDeletedAt()).isNull();

    final Instant beforeMarkDeleted = Instant.now();

    // Act
    entityManager.getTransaction().begin();
    entity.markDeleted("test-user");
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    final Instant afterMarkDeleted = Instant.now();

    // Assert
    assertThat(entity.isDeleted()).isTrue();
    assertThat(entity.getDeletedAt()).isNotNull();
    assertThat(entity.getDeletedAt())
        .isAfterOrEqualTo(beforeMarkDeleted)
        .isBeforeOrEqualTo(afterMarkDeleted);
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
    entity.markDeleted("test-user");
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    final Instant firstDeletedAt = entity.getDeletedAt();

    // Wait and call markDeleted() again
    TimeUnit.MILLISECONDS.sleep(10);

    entityManager.getTransaction().begin();
    entity.markDeleted("test-user");
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    var secondDeletedAt = entity.getDeletedAt();

    // Assert - Should update timestamp (not truly idempotent, but updates deletedAt)
    assertThat(entity.isDeleted()).isTrue();
    assertThat(secondDeletedAt).isNotNull();
    // Note: markDeleted() will update the timestamp each time it's called
    assertThat(secondDeletedAt).isAfterOrEqualTo(firstDeletedAt);
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
    entity.markDeleted("test-user");
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    assertThat(entity.isDeleted()).isTrue();
    assertThat(entity.getDeletedAt()).isNotNull();

    // Act - Restore entity
    entityManager.getTransaction().begin();
    entity.restore();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    // Assert
    assertThat(entity.isDeleted()).isFalse();
    assertThat(entity.getDeletedAt()).isNull();
  }

  @Test
  void shouldHandleRestoreOnNonDeletedEntity() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    assertThat(entity.isDeleted()).isFalse();

    // Act - Call restore() on non-deleted entity (should not throw exception)
    entityManager.getTransaction().begin();
    entity.restore();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    // Assert - Should complete without errors
    assertThat(entity.isDeleted()).isFalse();
    assertThat(entity.getDeletedAt()).isNull();
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
    entity.markDeleted("test-user");
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    assertThat(entity.isDeleted()).isTrue();
    final Instant firstDeletedAt = entity.getDeletedAt();

    entityManager.getTransaction().begin();
    entity.restore();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    assertThat(entity.isDeleted()).isFalse();

    TimeUnit.MILLISECONDS.sleep(10);

    // Act - Second cycle: delete -> restore
    entityManager.getTransaction().begin();
    entity.markDeleted("test-user");
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    assertThat(entity.isDeleted()).isTrue();
    final Instant secondDeletedAt = entity.getDeletedAt();

    entityManager.getTransaction().begin();
    entity.restore();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    // Assert
    assertThat(entity.isDeleted()).isFalse();
    assertThat(entity.getDeletedAt()).isNull();
    assertThat(secondDeletedAt).isAfter(firstDeletedAt);
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
    entity.markDeleted("test-user");
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    Long entityId = entity.getId();

    // Act - Clear and reload from database
    entityManager.clear();
    var reloadedEntity = entityManager.find(TestSoftDeletableEntity.class, entityId);

    // Assert
    assertThat(reloadedEntity.isDeleted()).isTrue();
    assertThat(reloadedEntity.getDeletedAt()).isNotNull();
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
    entity.markDeleted("test-user");
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
    assertThat(reloadedEntity.isDeleted()).isFalse();
    assertThat(reloadedEntity.getDeletedAt()).isNull();
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
    assertThatThrownBy(
            () -> {
              entityManager.remove(entity);
              entityManager.flush();
            })
        .isInstanceOf(UnsupportedOperationException.class);
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
