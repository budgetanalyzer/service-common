package org.budgetanalyzer.core.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import org.budgetanalyzer.core.domain.SoftDeletableEntity;

class SoftDeleteOperationsTest {

  private EntityManagerFactory entityManagerFactory;
  private EntityManager entityManager;
  private TestEntityRepository testEntityRepository;
  private TestStringIdEntityRepository testStringIdEntityRepository;

  @BeforeEach
  void setUp() {
    entityManagerFactory = Persistence.createEntityManagerFactory("test-pu");
    entityManager = entityManagerFactory.createEntityManager();

    var jpaRepositoryFactory = new JpaRepositoryFactory(entityManager);
    testEntityRepository = jpaRepositoryFactory.getRepository(TestEntityRepository.class);
    testStringIdEntityRepository =
        jpaRepositoryFactory.getRepository(TestStringIdEntityRepository.class);
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
  void shouldFindNotDeletedEntityByLongId() {
    var activeEntity = persistTestEntity("Active Entity", false);

    var result =
        executeInTransaction(() -> testEntityRepository.findByIdNotDeleted(activeEntity.getId()));

    assertThat(result).isPresent();
    assertThat(result).get().extracting(TestEntity::getName).isEqualTo("Active Entity");
  }

  @Test
  void shouldReturnEmptyForDeletedEntityByLongId() {
    var deletedEntity = persistTestEntity("Deleted Entity", true);

    var result =
        executeInTransaction(() -> testEntityRepository.findByIdNotDeleted(deletedEntity.getId()));

    assertThat(result).isEmpty();
  }

  @Test
  void shouldFindNotDeletedEntityByStringId() {
    var activeEntity = persistTestStringIdEntity("role_admin", "Admin", false);

    var result =
        executeInTransaction(
            () -> testStringIdEntityRepository.findByIdNotDeleted(activeEntity.getId()));

    assertThat(result).isPresent();
    assertThat(result).get().extracting(TestStringIdEntity::getName).isEqualTo("Admin");
  }

  @Test
  void shouldReturnEmptyForDeletedEntityByStringId() {
    var deletedEntity = persistTestStringIdEntity("role_deleted", "Deleted", true);

    var result =
        executeInTransaction(
            () -> testStringIdEntityRepository.findByIdNotDeleted(deletedEntity.getId()));

    assertThat(result).isEmpty();
  }

  @Test
  void shouldExcludeDeletedEntitiesFromFindAllNotDeleted() {
    persistTestEntity("Alpha", false);
    persistTestEntity("Bravo", true);
    persistTestEntity("Charlie", false);

    var result = executeInTransaction(() -> testEntityRepository.findAllNotDeleted());

    assertThat(result)
        .extracting(TestEntity::getName)
        .containsExactlyInAnyOrder("Alpha", "Charlie");
  }

  @Test
  void shouldPaginateNotDeletedEntitiesOnly() {
    persistTestEntity("Alpha", false);
    persistTestEntity("Bravo", true);
    persistTestEntity("Charlie", false);
    persistTestEntity("Delta", false);

    var pageRequest = PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "name"));

    var result = executeInTransaction(() -> testEntityRepository.findAllNotDeleted(pageRequest));

    assertThat(result.getTotalElements()).isEqualTo(3);
    assertThat(result.getContent())
        .extracting(TestEntity::getName)
        .containsExactly("Alpha", "Charlie");
  }

  @Test
  void shouldCombineSpecificationWithNotDeletedFilter() {
    persistTestEntity("Match One", false);
    persistTestEntity("Match Deleted", true);
    persistTestEntity("Other", false);

    Specification<TestEntity> nameContainsMatch =
        (root, query, criteriaBuilder) -> criteriaBuilder.like(root.get("name"), "Match%");

    var result =
        executeInTransaction(() -> testEntityRepository.findAllNotDeleted(nameContainsMatch));

    assertThat(result).extracting(TestEntity::getName).containsExactly("Match One");
  }

  @Test
  void shouldFindSingleNotDeletedEntityMatchingSpecification() {
    persistTestStringIdEntity("perm_read", "Read Permission", false);
    persistTestStringIdEntity("perm_deleted", "Read Deleted", true);

    Specification<TestStringIdEntity> nameStartsWithRead =
        (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("name"), "Read Permission");

    var result =
        executeInTransaction(
            () -> testStringIdEntityRepository.findOneNotDeleted(nameStartsWithRead));

    assertThat(result).isPresent();
    assertThat(result).get().extracting(TestStringIdEntity::getId).isEqualTo("perm_read");
  }

  @Test
  void shouldCountOnlyNotDeletedEntitiesMatchingSpecification() {
    persistTestStringIdEntity("perm_one", "Permission", false);
    persistTestStringIdEntity("perm_two", "Permission", false);
    persistTestStringIdEntity("perm_deleted", "Permission", true);
    persistTestStringIdEntity("perm_other", "Other", false);

    Specification<TestStringIdEntity> namedPermission =
        (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("name"), "Permission");

    var count =
        executeInTransaction(() -> testStringIdEntityRepository.countNotDeleted(namedPermission));

    assertThat(count).isEqualTo(2);
  }

  private TestEntity persistTestEntity(String name, boolean deleted) {
    var testEntity = new TestEntity();
    testEntity.setName(name);

    executeInTransaction(
        () -> {
          entityManager.persist(testEntity);
          if (deleted) {
            testEntity.markDeleted("test-user");
          }
          return testEntity;
        });

    entityManager.clear();
    return testEntity;
  }

  private TestStringIdEntity persistTestStringIdEntity(String id, String name, boolean deleted) {
    var testStringIdEntity = new TestStringIdEntity();
    testStringIdEntity.setId(id);
    testStringIdEntity.setName(name);

    executeInTransaction(
        () -> {
          entityManager.persist(testStringIdEntity);
          if (deleted) {
            testStringIdEntity.markDeleted("test-user");
          }
          return testStringIdEntity;
        });

    entityManager.clear();
    return testStringIdEntity;
  }

  private <T> T executeInTransaction(Supplier<T> supplier) {
    var entityTransaction = entityManager.getTransaction();
    entityTransaction.begin();
    try {
      var result = supplier.get();
      entityTransaction.commit();
      return result;
    } catch (RuntimeException runtimeException) {
      if (entityTransaction.isActive()) {
        entityTransaction.rollback();
      }
      throw runtimeException;
    }
  }

  interface TestEntityRepository
      extends JpaRepository<TestEntity, Long>, SoftDeleteOperations<TestEntity, Long> {}

  interface TestStringIdEntityRepository
      extends JpaRepository<TestStringIdEntity, String>,
          SoftDeleteOperations<TestStringIdEntity, String> {}

  /** Test entity with a Long identifier. */
  @Entity
  @Table(name = "test_soft_delete_operations")
  public static class TestEntity extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
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

  /** Test entity with a String identifier. */
  @Entity
  @Table(name = "test_soft_delete_operations_string_id")
  public static class TestStringIdEntity extends SoftDeletableEntity {

    @Id
    @Column(name = "id", nullable = false, length = 100)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    public String getId() {
      return id;
    }

    public void setId(String id) {
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
