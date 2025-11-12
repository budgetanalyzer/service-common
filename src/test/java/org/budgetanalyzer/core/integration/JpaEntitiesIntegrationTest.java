package org.budgetanalyzer.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.budgetanalyzer.core.integration.fixture.TestAuditableEntity;
import org.budgetanalyzer.core.integration.fixture.TestAuditableRepository;
import org.budgetanalyzer.core.integration.fixture.TestSoftDeletableEntity;
import org.budgetanalyzer.core.integration.fixture.TestSoftDeletableRepository;
import org.budgetanalyzer.service.integration.TestApplication;

/**
 * Integration test verifying base JPA entities work with Spring Data JPA.
 *
 * <p>Tests AuditableEntity and SoftDeletableEntity in a real Spring Data JPA context with H2
 * database.
 */
@SpringBootTest(classes = TestApplication.class)
@Transactional
@DisplayName("JPA Entities Integration Tests")
class JpaEntitiesIntegrationTest {

  @Autowired private TestAuditableRepository auditableRepository;

  @Autowired private TestSoftDeletableRepository softDeletableRepository;

  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    auditableRepository.deleteAll();
    softDeletableRepository.deleteAll();
  }

  @Test
  @DisplayName("Should automatically set createdAt on persist")
  void shouldSetCreatedAtOnPersist() {
    var entity = new TestAuditableEntity("Test Entity");

    assertThat(entity.getCreatedAt()).isNull();

    var saved = auditableRepository.save(entity);

    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("Should automatically set updatedAt on persist")
  void shouldSetUpdatedAtOnPersist() {
    var entity = new TestAuditableEntity("Test Entity");

    assertThat(entity.getUpdatedAt()).isNull();

    var saved = auditableRepository.save(entity);

    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("Should update updatedAt on entity modification")
  void shouldUpdateUpdatedAtOnModification() throws InterruptedException {
    var entity = new TestAuditableEntity("Original Name");
    var saved = auditableRepository.save(entity);
    entityManager.flush(); // Force persist
    final var originalUpdatedAt = saved.getUpdatedAt();

    // Sleep to ensure timestamp difference (100ms to account for nanosecond precision)
    Thread.sleep(100);

    saved.setName("Modified Name");
    var updated = auditableRepository.save(saved);
    entityManager.flush(); // Force update

    assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
    assertThat(updated.getCreatedAt()).isEqualTo(saved.getCreatedAt());
  }

  @Test
  @DisplayName("Should not modify createdAt on entity update")
  void shouldNotModifyCreatedAtOnUpdate() throws InterruptedException {
    var entity = new TestAuditableEntity("Original Name");
    var saved = auditableRepository.save(entity);
    entityManager.flush(); // Force persist
    final var originalCreatedAt = saved.getCreatedAt();

    // Sleep to ensure any timestamp changes would be detectable
    Thread.sleep(100);

    saved.setName("Modified Name");
    var updated = auditableRepository.save(saved);
    entityManager.flush(); // Force update

    assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
  }

  @Test
  @DisplayName("Should set deleted flag to false by default")
  void shouldSetDeletedFlagToFalseByDefault() {
    var entity = new TestSoftDeletableEntity("Test Entity");
    var saved = softDeletableRepository.save(entity);

    assertThat(saved.isDeleted()).isFalse();
  }

  @Test
  @DisplayName("Should mark entity as deleted when deleted flag is set")
  void shouldMarkEntityAsDeleted() {
    var entity = new TestSoftDeletableEntity("Test Entity");
    var saved = softDeletableRepository.save(entity);

    saved.markDeleted();
    var deleted = softDeletableRepository.save(saved);

    assertThat(deleted.isDeleted()).isTrue();
  }

  @Test
  @DisplayName("Should find only non-deleted entities with custom query")
  void shouldFindOnlyNonDeletedEntities() {
    var entity1 = new TestSoftDeletableEntity("Entity 1");
    var entity2 = new TestSoftDeletableEntity("Entity 2");
    var entity3 = new TestSoftDeletableEntity("Entity 3");

    softDeletableRepository.save(entity1);
    var saved2 = softDeletableRepository.save(entity2);
    softDeletableRepository.save(entity3);

    // Mark entity2 as deleted
    saved2.markDeleted();
    softDeletableRepository.save(saved2);

    var nonDeletedEntities = softDeletableRepository.findByDeletedFalse();

    assertThat(nonDeletedEntities).hasSize(2);
    assertThat(nonDeletedEntities)
        .extracting(TestSoftDeletableEntity::getName)
        .containsExactlyInAnyOrder("Entity 1", "Entity 3");
  }

  @Test
  @DisplayName("Should find only deleted entities with custom query")
  void shouldFindOnlyDeletedEntities() {
    var entity1 = new TestSoftDeletableEntity("Entity 1");
    var entity2 = new TestSoftDeletableEntity("Entity 2");
    var entity3 = new TestSoftDeletableEntity("Entity 3");

    var saved1 = softDeletableRepository.save(entity1);
    softDeletableRepository.save(entity2);
    var saved3 = softDeletableRepository.save(entity3);

    // Mark entity1 and entity3 as deleted
    saved1.markDeleted();
    saved3.markDeleted();
    softDeletableRepository.save(saved1);
    softDeletableRepository.save(saved3);

    var deletedEntities = softDeletableRepository.findByDeletedTrue();

    assertThat(deletedEntities).hasSize(2);
    assertThat(deletedEntities)
        .extracting(TestSoftDeletableEntity::getName)
        .containsExactlyInAnyOrder("Entity 1", "Entity 3");
  }

  @Test
  @DisplayName("Should persist soft-deleted entities in database")
  void shouldPersistSoftDeletedEntitiesInDatabase() {
    var entity = new TestSoftDeletableEntity("Test Entity");
    var saved = softDeletableRepository.save(entity);

    saved.markDeleted();
    softDeletableRepository.save(saved);

    // findAll() still finds deleted entities (no @Where clause in test entity)
    var allEntities = softDeletableRepository.findAll();
    assertThat(allEntities).hasSize(1);
    assertThat(allEntities.get(0).isDeleted()).isTrue();
  }

  @Test
  @DisplayName("Should inherit auditable fields in soft-deletable entity")
  void shouldInheritAuditableFieldsInSoftDeletableEntity() {
    var entity = new TestSoftDeletableEntity("Test Entity");

    assertThat(entity.getCreatedAt()).isNull();
    assertThat(entity.getUpdatedAt()).isNull();

    var saved = softDeletableRepository.save(entity);

    // SoftDeletableEntity extends AuditableEntity
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }
}
