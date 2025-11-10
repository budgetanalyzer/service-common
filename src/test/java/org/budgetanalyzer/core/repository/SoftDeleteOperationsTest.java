package org.budgetanalyzer.core.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

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
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import org.budgetanalyzer.core.domain.SoftDeletableEntity;

class SoftDeleteOperationsTest {

  private EntityManagerFactory entityManagerFactory;
  private EntityManager entityManager;
  private TestRepository repository;

  @BeforeEach
  void setUp() {
    entityManagerFactory = Persistence.createEntityManagerFactory("test-pu");
    entityManager = entityManagerFactory.createEntityManager();
    repository = new TestRepository(entityManager);
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
  void shouldFindAllActiveEntitiesOnly() {
    // Arrange
    final var active1 = createEntity("Active 1", BigDecimal.valueOf(100));
    final var active2 = createEntity("Active 2", BigDecimal.valueOf(200));
    final var deleted = createEntity("Deleted", BigDecimal.valueOf(300));

    entityManager.getTransaction().begin();
    entityManager.persist(active1);
    entityManager.persist(active2);
    entityManager.persist(deleted);
    entityManager.getTransaction().commit();

    entityManager.getTransaction().begin();
    deleted.markDeleted();
    entityManager.merge(deleted);
    entityManager.getTransaction().commit();

    entityManager.clear();

    // Act
    var results = repository.findAllActive();

    // Assert
    assertEquals(2, results.size(), "Should only return active entities");
    assertTrue(
        results.stream().noneMatch(TestEntity::isDeleted),
        "Results should not contain deleted entities");
    assertTrue(
        results.stream().anyMatch(e -> e.getName().equals("Active 1")), "Should contain Active 1");
    assertTrue(
        results.stream().anyMatch(e -> e.getName().equals("Active 2")), "Should contain Active 2");
  }

  @Test
  void shouldFindByIdActiveReturnEmptyForDeletedEntity() {
    // Arrange
    var entity = createEntity("Test Entity", BigDecimal.valueOf(100));

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    final Long entityId = entity.getId();

    entityManager.getTransaction().begin();
    entity.markDeleted();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    entityManager.clear();

    // Act
    var result = repository.findByIdActive(entityId);

    // Assert
    assertTrue(result.isEmpty(), "Should return empty Optional for deleted entity");
  }

  @Test
  void shouldFindByIdActiveReturnEntityForActiveEntity() {
    // Arrange
    var entity = createEntity("Test Entity", BigDecimal.valueOf(100));

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    Long entityId = entity.getId();
    entityManager.clear();

    // Act
    var result = repository.findByIdActive(entityId);

    // Assert
    assertTrue(result.isPresent(), "Should return entity for active entity");
    assertEquals("Test Entity", result.get().getName());
    assertFalse(result.get().isDeleted());
  }

  @Test
  void shouldFindAllActiveWithPagination() {
    // Arrange - Create 5 active and 2 deleted entities
    for (int i = 1; i <= 5; i++) {
      var entity = createEntity("Active " + i, BigDecimal.valueOf(i * 100));
      entityManager.getTransaction().begin();
      entityManager.persist(entity);
      entityManager.getTransaction().commit();
    }

    for (int i = 1; i <= 2; i++) {
      var entity = createEntity("Deleted " + i, BigDecimal.valueOf(i * 1000));
      entityManager.getTransaction().begin();
      entityManager.persist(entity);
      entityManager.getTransaction().commit();

      entityManager.getTransaction().begin();
      entity.markDeleted();
      entityManager.merge(entity);
      entityManager.getTransaction().commit();
    }

    entityManager.clear();

    // Act - Page 1 with size 2
    var page1 = repository.findAllActive(PageRequest.of(0, 2, Sort.by("name")));

    // Assert
    assertEquals(5, page1.getTotalElements(), "Should have 5 total active entities");
    assertEquals(3, page1.getTotalPages(), "Should have 3 pages (5 entities / 2 per page)");
    assertEquals(2, page1.getContent().size(), "First page should have 2 entities");
    assertTrue(page1.hasNext(), "Should have next page");
    assertFalse(page1.getContent().get(0).isDeleted(), "Should not contain deleted entities");
    assertFalse(page1.getContent().get(1).isDeleted(), "Should not contain deleted entities");
  }

  @Test
  void shouldFindAllActiveWithCustomSpecification() {
    // Arrange
    final var lowAmount1 = createEntity("Low 1", BigDecimal.valueOf(50));
    final var lowAmount2 = createEntity("Low 2", BigDecimal.valueOf(75));
    final var highAmount1 = createEntity("High 1", BigDecimal.valueOf(150));
    final var highAmount2 = createEntity("High 2", BigDecimal.valueOf(200));
    final var deletedHigh = createEntity("Deleted High", BigDecimal.valueOf(300));

    entityManager.getTransaction().begin();
    entityManager.persist(lowAmount1);
    entityManager.persist(lowAmount2);
    entityManager.persist(highAmount1);
    entityManager.persist(highAmount2);
    entityManager.persist(deletedHigh);
    entityManager.getTransaction().commit();

    entityManager.getTransaction().begin();
    deletedHigh.markDeleted();
    entityManager.merge(deletedHigh);
    entityManager.getTransaction().commit();

    entityManager.clear();

    // Custom specification: amount > 100
    Specification<TestEntity> amountGreaterThan100 =
        (root, query, cb) -> cb.greaterThan(root.get("amount"), BigDecimal.valueOf(100));

    // Act
    var results = repository.findAllActive(amountGreaterThan100);

    // Assert
    assertEquals(2, results.size(), "Should return 2 entities with amount > 100 (not deleted)");
    assertTrue(
        results.stream().allMatch(e -> e.getAmount().compareTo(BigDecimal.valueOf(100)) > 0),
        "All results should have amount > 100");
    assertTrue(
        results.stream().noneMatch(TestEntity::isDeleted), "Results should not contain deleted");
    assertTrue(results.stream().anyMatch(e -> e.getName().equals("High 1")));
    assertTrue(results.stream().anyMatch(e -> e.getName().equals("High 2")));
  }

  @Test
  void shouldFindAllActiveWithSpecificationAndPagination() {
    // Arrange - Create entities with various amounts
    for (int i = 1; i <= 10; i++) {
      var entity = createEntity("Entity " + i, BigDecimal.valueOf(i * 10));
      entityManager.getTransaction().begin();
      entityManager.persist(entity);
      entityManager.getTransaction().commit();
    }

    // Mark some as deleted
    var entities =
        entityManager.createQuery("SELECT e FROM TestEntity e", TestEntity.class).getResultList();
    entityManager.getTransaction().begin();
    entities.get(0).markDeleted(); // Entity 1
    entities.get(9).markDeleted(); // Entity 10
    entityManager.getTransaction().commit();

    entityManager.clear();

    // Custom specification: amount >= 50
    Specification<TestEntity> amountGreaterThanOrEqual50 =
        (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), BigDecimal.valueOf(50));

    // Act
    var page = repository.findAllActive(amountGreaterThanOrEqual50, PageRequest.of(0, 3));

    // Assert
    assertEquals(5, page.getTotalElements(), "Should have 5 active entities with amount >= 50");
    assertEquals(2, page.getTotalPages(), "Should have 2 pages (5 entities / 3 per page)");
    assertEquals(3, page.getContent().size(), "First page should have 3 entities");
    assertTrue(
        page.getContent().stream()
            .allMatch(e -> e.getAmount().compareTo(BigDecimal.valueOf(50)) >= 0),
        "All results should have amount >= 50");
    assertTrue(
        page.getContent().stream().noneMatch(TestEntity::isDeleted),
        "Results should not contain deleted");
  }

  @Test
  void shouldCountActiveWithSpecification() {
    // Arrange
    for (int i = 1; i <= 10; i++) {
      var entity = createEntity("Entity " + i, BigDecimal.valueOf(i * 10));
      entityManager.getTransaction().begin();
      entityManager.persist(entity);
      entityManager.getTransaction().commit();
    }

    // Mark some as deleted
    var entities =
        entityManager.createQuery("SELECT e FROM TestEntity e", TestEntity.class).getResultList();
    entityManager.getTransaction().begin();
    entities.get(0).markDeleted();
    entities.get(1).markDeleted();
    entityManager.getTransaction().commit();

    entityManager.clear();

    // Custom specification: amount >= 50
    Specification<TestEntity> amountGreaterThanOrEqual50 =
        (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), BigDecimal.valueOf(50));

    // Act
    long count = repository.countActive(amountGreaterThanOrEqual50);

    // Assert
    assertEquals(6, count, "Should count 6 active entities with amount >= 50");
  }

  @Test
  void shouldFindOneActiveWithSpecification() {
    // Arrange
    var entity1 = createEntity("Unique Name", BigDecimal.valueOf(100));
    var entity2 = createEntity("Other Name", BigDecimal.valueOf(200));

    entityManager.getTransaction().begin();
    entityManager.persist(entity1);
    entityManager.persist(entity2);
    entityManager.getTransaction().commit();

    entityManager.clear();

    // Custom specification: name = "Unique Name"
    Specification<TestEntity> nameEquals =
        (root, query, cb) -> cb.equal(root.get("name"), "Unique Name");

    // Act
    var result = repository.findOneActive(nameEquals);

    // Assert
    assertTrue(result.isPresent(), "Should find entity with unique name");
    assertEquals("Unique Name", result.get().getName());
    assertFalse(result.get().isDeleted());
  }

  @Test
  void shouldFindOneActiveReturnEmptyForDeletedEntity() {
    // Arrange
    var entity = createEntity("Unique Name", BigDecimal.valueOf(100));

    entityManager.getTransaction().begin();
    entityManager.persist(entity);
    entityManager.getTransaction().commit();

    entityManager.getTransaction().begin();
    entity.markDeleted();
    entityManager.merge(entity);
    entityManager.getTransaction().commit();

    entityManager.clear();

    // Custom specification: name = "Unique Name"
    Specification<TestEntity> nameEquals =
        (root, query, cb) -> cb.equal(root.get("name"), "Unique Name");

    // Act
    var result = repository.findOneActive(nameEquals);

    // Assert
    assertTrue(result.isEmpty(), "Should return empty for deleted entity");
  }

  @Test
  void shouldCombineMultipleSpecifications() {
    // Arrange
    final var entity1 = createEntity("Alpha", BigDecimal.valueOf(50));
    final var entity2 = createEntity("Beta", BigDecimal.valueOf(150));
    final var entity3 = createEntity("Gamma", BigDecimal.valueOf(250));
    final var entity4 = createEntity("Delta", BigDecimal.valueOf(350));

    entityManager.getTransaction().begin();
    entityManager.persist(entity1);
    entityManager.persist(entity2);
    entityManager.persist(entity3);
    entityManager.persist(entity4);
    entityManager.getTransaction().commit();

    entityManager.getTransaction().begin();
    entity4.markDeleted();
    entityManager.merge(entity4);
    entityManager.getTransaction().commit();

    entityManager.clear();

    // Combine specifications: amount > 100 AND amount < 300
    Specification<TestEntity> amountGreaterThan100 =
        (root, query, cb) -> cb.greaterThan(root.get("amount"), BigDecimal.valueOf(100));
    Specification<TestEntity> amountLessThan300 =
        (root, query, cb) -> cb.lessThan(root.get("amount"), BigDecimal.valueOf(300));

    Specification<TestEntity> combined = amountGreaterThan100.and(amountLessThan300);

    // Act
    var results = repository.findAllActive(combined);

    // Assert
    assertEquals(2, results.size(), "Should return 2 entities matching combined criteria");
    assertTrue(results.stream().anyMatch(e -> e.getName().equals("Beta")));
    assertTrue(results.stream().anyMatch(e -> e.getName().equals("Gamma")));
    assertFalse(results.stream().anyMatch(e -> e.getName().equals("Delta")), "Deleted excluded");
  }

  @Test
  void shouldHandleEmptyResultsGracefully() {
    // Arrange - No entities in database
    entityManager.clear();

    // Act
    var results = repository.findAllActive();
    var pageResults = repository.findAllActive(PageRequest.of(0, 10));

    // Assert
    assertNotNull(results, "Should return non-null list");
    assertTrue(results.isEmpty(), "Should return empty list");
    assertNotNull(pageResults, "Should return non-null page");
    assertEquals(0, pageResults.getTotalElements(), "Page should have 0 total elements");
    assertTrue(pageResults.getContent().isEmpty(), "Page content should be empty");
  }

  @Test
  void shouldHandleAllDeletedEntities() {
    // Arrange - Create and delete all entities
    for (int i = 1; i <= 5; i++) {
      var entity = createEntity("Entity " + i, BigDecimal.valueOf(i * 100));
      entityManager.getTransaction().begin();
      entityManager.persist(entity);
      entityManager.getTransaction().commit();
    }

    var entities =
        entityManager.createQuery("SELECT e FROM TestEntity e", TestEntity.class).getResultList();
    entityManager.getTransaction().begin();
    entities.forEach(TestEntity::markDeleted);
    entityManager.getTransaction().commit();

    entityManager.clear();

    // Act
    var results = repository.findAllActive();

    // Assert
    assertTrue(results.isEmpty(), "Should return empty list when all entities are deleted");
  }

  private TestEntity createEntity(String name, BigDecimal amount) {
    var entity = new TestEntity();
    entity.setName(name);
    entity.setAmount(amount);
    return entity;
  }

  // Test repository implementation
  static class TestRepository extends SimpleJpaRepository<TestEntity, Long>
      implements SoftDeleteOperations<TestEntity> {

    public TestRepository(EntityManager entityManager) {
      super(TestEntity.class, entityManager);
    }
  }

  // Test entity
  @Entity(name = "TestEntity")
  @Table(name = "test_entity")
  static class TestEntity extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "amount")
    private BigDecimal amount;

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

    public BigDecimal getAmount() {
      return amount;
    }

    public void setAmount(BigDecimal amount) {
      this.amount = amount;
    }
  }
}
