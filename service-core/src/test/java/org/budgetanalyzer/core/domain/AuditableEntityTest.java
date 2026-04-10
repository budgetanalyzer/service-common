package org.budgetanalyzer.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isNotNull();
    assertThat(entity.getCreatedAt())
        .isAfterOrEqualTo(beforePersist)
        .isBeforeOrEqualTo(afterPersist);
    assertThat(entity.getUpdatedAt())
        .isAfterOrEqualTo(beforePersist)
        .isBeforeOrEqualTo(afterPersist);
    // createdAt and updatedAt should be very close (within 1 millisecond)
    var timeDiffMillis = Duration.between(entity.getCreatedAt(), entity.getUpdatedAt()).toMillis();
    assertThat(Math.abs(timeDiffMillis)).isLessThanOrEqualTo(1);
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
    assertThat(entity.getCreatedAt()).isEqualTo(originalCreatedAt);
    assertThat(entity.getUpdatedAt()).isNotEqualTo(originalUpdatedAt);
    assertThat(entity.getUpdatedAt()).isAfter(originalUpdatedAt);
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

    var thirdUpdatedAt = entity.getUpdatedAt();

    // Assert
    assertThat(secondUpdatedAt).isAfter(firstUpdatedAt);
    assertThat(thirdUpdatedAt).isAfter(secondUpdatedAt);
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
        Duration.between(originalCreatedAt, reloadedEntity.getCreatedAt()).toMillis();
    assertThat(Math.abs(timeDiffMillis)).isLessThanOrEqualTo(1);
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
    assertThat(entity1.getCreatedAt()).isNotNull();
    assertThat(entity2.getCreatedAt()).isNotNull();
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
    var createdAt = entity.getCreatedAt();
    var updatedAt = entity.getUpdatedAt();

    // Assert
    assertThat(createdAt).isInstanceOf(Instant.class);
    assertThat(updatedAt).isInstanceOf(Instant.class);
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
    var timeDiffMillis = Duration.between(entity.getCreatedAt(), entity.getUpdatedAt()).toMillis();
    assertThat(Math.abs(timeDiffMillis)).isLessThanOrEqualTo(1);
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
    assertThat(entity.getCreatedAt()).isEqualTo(originalCreatedAt);
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
    assertThat(entity.getCreatedBy()).isNull();
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
    assertThat(entity.getUpdatedBy()).isNull();
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
    assertThat(reloadedEntity.getCreatedBy()).isNull();
    assertThat(reloadedEntity.getUpdatedBy()).isNull();
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
