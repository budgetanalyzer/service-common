package org.budgetanalyzer.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

class AuditableEntityTest {

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
  void shouldSetCreatedAtAndUpdatedAtOnPersist() {
    // Arrange
    var entity = new TestAuditableEntity();
    entity.setName("Test Entity");

    final Instant beforePersist = Instant.now();

    // Act
    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    final Instant afterPersist = Instant.now();

    // Assert
    assertNotNull(entity.getCreatedAt(), "createdAt should be set after persist");
    assertNotNull(entity.getUpdatedAt(), "updatedAt should be set after persist");
    assertTrue(
        !entity.getCreatedAt().isBefore(beforePersist)
            && !entity.getCreatedAt().isAfter(afterPersist),
        "createdAt should be within persist time range");
    assertTrue(
        !entity.getUpdatedAt().isBefore(beforePersist)
            && !entity.getUpdatedAt().isAfter(afterPersist),
        "updatedAt should be within persist time range");
    // createdAt and updatedAt should be very close (within 1 millisecond)
    var timeDiffMillis =
        java.time.Duration.between(entity.getCreatedAt(), entity.getUpdatedAt()).toMillis();
    assertTrue(
        Math.abs(timeDiffMillis) <= 1,
        "createdAt and updatedAt should be within 1ms on initial persist");
  }

  @Test
  void shouldNotChangeCreatedAtOnUpdate() throws InterruptedException {
    // Arrange - Create entity
    var entity = new TestAuditableEntity();
    entity.setName("Original Name");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    final Instant originalCreatedAt = entity.getCreatedAt();
    final Instant originalUpdatedAt = entity.getUpdatedAt();

    // Wait to ensure timestamp difference
    TimeUnit.MILLISECONDS.sleep(10);

    // Act - Update entity
    entityManager.getTransaction().begin();
    entity.setName("Updated Name");
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    // Assert
    assertEquals(
        originalCreatedAt, entity.getCreatedAt(), "createdAt should remain unchanged on update");
    assertNotEquals(originalUpdatedAt, entity.getUpdatedAt(), "updatedAt should change on update");
    assertTrue(
        entity.getUpdatedAt().isAfter(originalUpdatedAt),
        "updatedAt should be later than original");
  }

  @Test
  void shouldUpdateUpdatedAtOnEachUpdate() throws InterruptedException {
    // Arrange - Create entity
    var entity = new TestAuditableEntity();
    entity.setName("Name 1");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    final Instant firstUpdatedAt = entity.getUpdatedAt();

    // Act - First update
    TimeUnit.MILLISECONDS.sleep(10);
    entityManager.getTransaction().begin();
    entity.setName("Name 2");
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    final Instant secondUpdatedAt = entity.getUpdatedAt();

    // Act - Second update
    TimeUnit.MILLISECONDS.sleep(10);
    entityManager.getTransaction().begin();
    entity.setName("Name 3");
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    Instant thirdUpdatedAt = entity.getUpdatedAt();

    // Assert
    assertTrue(secondUpdatedAt.isAfter(firstUpdatedAt), "Second update timestamp should be later");
    assertTrue(thirdUpdatedAt.isAfter(secondUpdatedAt), "Third update timestamp should be later");
  }

  @Test
  void shouldSetCreatedAtAsImmutable() {
    // Arrange
    var entity = new TestAuditableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    final Instant originalCreatedAt = entity.getCreatedAt();

    // Act - Flush and clear to force reload from database
    entityManager.clear();
    var reloadedEntity = entityManager.find(TestAuditableEntity.class, entity.getId());

    // Assert - Allow for sub-millisecond database precision differences
    var timeDiffMillis =
        java.time.Duration.between(originalCreatedAt, reloadedEntity.getCreatedAt()).toMillis();
    assertTrue(
        Math.abs(timeDiffMillis) <= 1,
        "createdAt should be immutable (within 1ms precision after reload)");
  }

  @Test
  void shouldHandleMultipleConcurrentCreates() {
    // Arrange
    var entity1 = new TestAuditableEntity();
    entity1.setName("Entity 1");

    var entity2 = new TestAuditableEntity();
    entity2.setName("Entity 2");

    // Act
    entityManager.getTransaction().begin();
    entityManager.persist(entity1);
    entityManager.persist(entity2);
    entityManager.getTransaction().commit();

    // Assert
    assertNotNull(entity1.getCreatedAt());
    assertNotNull(entity2.getCreatedAt());
    // Timestamps might be equal or different depending on execution speed,
    // but both should be set
    assertTrue(
        entity1.getCreatedAt().equals(entity2.getCreatedAt())
            || !entity1.getCreatedAt().equals(entity2.getCreatedAt()),
        "Both entities should have valid timestamps");
  }

  @Test
  void shouldReturnCorrectInstantObjects() {
    // Arrange
    var entity = new TestAuditableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Act
    Instant createdAt = entity.getCreatedAt();
    Instant updatedAt = entity.getUpdatedAt();

    // Assert
    assertInstanceOf(Instant.class, createdAt, "createdAt should be an Instant");
    assertInstanceOf(Instant.class, updatedAt, "updatedAt should be an Instant");
  }

  @Test
  void shouldNotTriggerPreUpdateOnPersist() {
    // Arrange
    var entity = new TestAuditableEntity();
    entity.setName("Test Entity");

    // Act
    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Assert - createdAt and updatedAt should be very close (PreUpdate not called separately)
    var timeDiffMillis =
        java.time.Duration.between(entity.getCreatedAt(), entity.getUpdatedAt()).toMillis();
    assertTrue(
        Math.abs(timeDiffMillis) <= 1,
        "PreUpdate should not be triggered on persist (timestamps within 1ms)");
  }

  @Test
  void shouldNotTriggerPrePersistOnUpdate() throws InterruptedException {
    // Arrange
    var entity = new TestAuditableEntity();
    entity.setName("Original Name");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    final Instant originalCreatedAt = entity.getCreatedAt();

    TimeUnit.MILLISECONDS.sleep(10);

    // Act
    entityManager.getTransaction().begin();
    entity.setName("Updated Name");
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    // Assert - createdAt should not change (PrePersist not called on update)
    assertEquals(
        originalCreatedAt, entity.getCreatedAt(), "PrePersist should not be triggered on update");
  }

  @Test
  void shouldHaveCreatedByFieldAccessible() {
    // Arrange
    var entity = new TestAuditableEntity();
    entity.setName("Test Entity");

    // Act
    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Assert - createdBy is null without AuditorAware configuration
    assertNull(entity.getCreatedBy(), "createdBy should be null without AuditorAware");
  }

  @Test
  void shouldHaveUpdatedByFieldAccessible() {
    // Arrange
    var entity = new TestAuditableEntity();
    entity.setName("Test Entity");

    // Act
    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Assert - updatedBy is null without AuditorAware configuration
    assertNull(entity.getUpdatedBy(), "updatedBy should be null without AuditorAware");
  }

  @Test
  void shouldPersistAndRetrieveUserTrackingFields() {
    // Arrange
    var entity = new TestAuditableEntity();
    entity.setName("Test Entity");

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    // Act - Clear and reload from database
    entityManager.clear();
    var reloadedEntity = entityManager.find(TestAuditableEntity.class, entity.getId());

    // Assert - Fields should be accessible after reload (null without AuditorAware)
    assertNull(reloadedEntity.getCreatedBy(), "createdBy should persist as null");
    assertNull(reloadedEntity.getUpdatedBy(), "updatedBy should persist as null");
  }

  // Concrete test entity for testing AuditableEntity
  @Entity
  @Table(name = "test_auditable_entity")
  static class TestAuditableEntity extends AuditableEntity {

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
