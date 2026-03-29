package org.budgetanalyzer.core.integration.fixture;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import org.budgetanalyzer.core.repository.SoftDeleteOperations;

/** Test repository for TestSoftDeletableEntity. */
public interface TestSoftDeletableRepository
    extends JpaRepository<TestSoftDeletableEntity, Long>,
        SoftDeleteOperations<TestSoftDeletableEntity, Long> {

  /**
   * Find all non-deleted entities.
   *
   * @return list of non-deleted entities
   */
  List<TestSoftDeletableEntity> findByDeletedFalse();

  /**
   * Find all deleted entities.
   *
   * @return list of deleted entities
   */
  List<TestSoftDeletableEntity> findByDeletedTrue();
}
