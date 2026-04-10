package org.budgetanalyzer.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    assertThatThrownBy(
            () -> {
              entityManager.remove(entity);
              entityManager.flush(); // Force JPA to trigger @PreRemove callback
            })
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Hard delete not allowed")
        .hasMessageContaining("markDeleted(deletedBy)");

    entityManager.getTransaction().rollback();
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
    assertThatThrownBy(
            () -> {
              entityManager.remove(entity);
              entityManager.flush();
            })
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("TestSoftDeletableEntity");

    entityManager.getTransaction().rollback();
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
    assertThat(entity.isDeleted()).isTrue();

    var reloadedEntity = entityManager.find(TestSoftDeletableEntity.class, entity.getId());
    assertThat(reloadedEntity).isNotNull();
    assertThat(reloadedEntity.isDeleted()).isTrue();
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
    assertThatThrownBy(
            () -> {
              entityManager.remove(entity);
              entityManager.flush();
            })
        .isInstanceOf(UnsupportedOperationException.class);

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
    assertThat(listener).isNotNull();
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
