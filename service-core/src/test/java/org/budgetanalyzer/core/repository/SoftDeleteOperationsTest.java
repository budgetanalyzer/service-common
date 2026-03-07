package org.budgetanalyzer.core.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import org.budgetanalyzer.core.domain.SoftDeletableEntity;

class SoftDeleteOperationsTest {

  private TestSoftDeleteOperations softDeleteOperations;

  @BeforeEach
  void setUp() {
    softDeleteOperations = mock(TestSoftDeleteOperations.class);

    // Allow default methods to execute
    when(softDeleteOperations.notDeleted()).thenCallRealMethod();
    when(softDeleteOperations.countActive()).thenCallRealMethod();
    when(softDeleteOperations.countActive(any())).thenCallRealMethod();
  }

  @Test
  void countActiveShouldReturnCountOfAllActiveEntities() {
    when(softDeleteOperations.count(any())).thenReturn(5L);

    var result = softDeleteOperations.countActive();

    assertEquals(5L, result);
  }

  @Test
  void countActiveShouldReturnZeroWhenNoActiveEntities() {
    when(softDeleteOperations.count(any())).thenReturn(0L);

    var result = softDeleteOperations.countActive();

    assertEquals(0L, result);
  }

  @Test
  void countActiveWithSpecShouldReturnCountOfMatchingActiveEntities() {
    when(softDeleteOperations.count(any())).thenReturn(3L);

    Specification<TestEntity> spec = (root, query, cb) -> cb.equal(root.get("name"), "test");
    var result = softDeleteOperations.countActive(spec);

    assertEquals(3L, result);
  }

  @Test
  void countActiveWithSpecShouldReturnZeroWhenNoMatches() {
    when(softDeleteOperations.count(any())).thenReturn(0L);

    Specification<TestEntity> spec = (root, query, cb) -> cb.equal(root.get("name"), "nonexistent");
    var result = softDeleteOperations.countActive(spec);

    assertEquals(0L, result);
  }

  abstract static class TestEntity extends SoftDeletableEntity {}

  interface TestSoftDeleteOperations extends SoftDeleteOperations<TestEntity> {}
}
