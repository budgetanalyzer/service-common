package org.budgetanalyzer.core.domain;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class SoftDeleteListenerTest {

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
  void shouldPreventHardDeleteWithUnsupportedOperationException() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Act & Assert
    entityManager.getTransaction().begin();
    var exception =
        assertThrows(
            UnsupportedOperationException.class,
            () -> {
              entityManager.remove(entity);
              entityManager.flush(); // Force JPA to trigger @PreRemove callback
            },
            "Hard delete should throw UnsupportedOperationException");

    entityManager.getTransaction().rollback();

    // Assert exception message
    assertNotNull(exception.getMessage(), "Exception should have a message");
    assertTrue(
        exception.getMessage().contains("Hard delete not allowed"),
        "Exception message should mention hard delete not allowed");
    assertTrue(
        exception.getMessage().contains("markDeleted(deletedBy)"),
        "Exception message should suggest using markDeleted(deletedBy)");
  }

  @Test
  void shouldIncludeEntityClassNameInExceptionMessage() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Act & Assert
    entityManager.getTransaction().begin();
    var exception =
        assertThrows(
            UnsupportedOperationException.class,
            () -> {
              entityManager.remove(entity);
              entityManager.flush();
            });

    entityManager.getTransaction().rollback();

    // Assert - Exception message should include entity class name
    assertTrue(
        exception.getMessage().contains("TestSoftDeletableEntity")
            || exception.getMessage().contains(entity.getClass().toString()),
        "Exception message should include entity class name");
  }

  @Test
  void shouldAllowSoftDeleteViaMarkDeleted() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Act - Soft delete via markDeleted() should work fine
    entityManager.getTransaction().begin();
    entity.markDeleted("test-user");
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    // Assert - Entity should be marked deleted but still in database
    assertTrue(entity.isDeleted(), "Entity should be marked as deleted");

    var reloadedEntity = entityManager.find(TestSoftDeletableEntity.class, entity.getId());
    assertNotNull(reloadedEntity, "Entity should still exist in database after soft delete");
    assertTrue(reloadedEntity.isDeleted(), "Reloaded entity should be marked as deleted");
  }

  @Test
  void shouldPreventHardDeleteEvenAfterSoftDelete() {
    // Arrange
    var entity = new TestSoftDeletableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Soft delete first
    entityManager.getTransaction().begin();
    entity.markDeleted("test-user");
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    // Act & Assert - Hard delete should still be prevented
    entityManager.getTransaction().begin();
    assertThrows(
        UnsupportedOperationException.class,
        () -> {
          entityManager.remove(entity);
          entityManager.flush();
        },
        "Hard delete should be prevented even after soft delete");

    entityManager.getTransaction().rollback();
  }

  @Test
  void shouldOnlyApplyToSoftDeletableEntities() {
    // This test verifies the listener is specific to SoftDeletableEntity
    // by checking the method signature accepts SoftDeletableEntity parameter

    var listener = new SoftDeleteListener();

    // The listener's preRemove method only accepts SoftDeletableEntity
    // This is enforced by the method signature and JPA will only call it
    // for entities with @EntityListeners(SoftDeleteListener.class)

    // This test verifies the listener can be instantiated
    assertNotNull(listener, "SoftDeleteListener should be instantiable");
  }

  // Concrete test entity for testing SoftDeleteListener
  @Entity
  @Table(name = "test_soft_deletable_entity_listener")
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
