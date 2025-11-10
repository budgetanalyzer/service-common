package org.budgetanalyzer.core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import org.budgetanalyzer.core.domain.SoftDeletableEntity;

/**
 * Repository interface providing query methods that automatically filter out soft-deleted entities.
 *
 * <p>This interface should be extended by repositories for entities that extend {@link
 * SoftDeletableEntity}. It provides "active" versions of common query methods that exclude
 * soft-deleted records (where {@code deleted = true}).
 *
 * <p>All methods use JPA Specifications to dynamically add the {@code deleted = false} filter to
 * queries. This interface extends {@link JpaSpecificationExecutor} to provide the underlying query
 * capabilities.
 *
 * <p>Example usage:
 *
 * <pre>
 * public interface TransactionRepository extends JpaRepository&lt;Transaction, Long&gt;,
 *                                                SoftDeleteOperations&lt;Transaction, Long&gt; {
 *     // You can add custom query methods here
 * }
 *
 * // In service layer
 * var allActive = repository.findAllActive();
 * var transaction = repository.findByIdActive(id)
 *     .orElseThrow(() -&gt; new ResourceNotFoundException("Transaction not found"));
 *
 * var activePage = repository.findAllActive(PageRequest.of(0, 10));
 *
 * // With custom specifications
 * Specification&lt;Transaction&gt; amountGreaterThan = (root, query, cb) -&gt;
 *     cb.greaterThan(root.get("amount"), BigDecimal.valueOf(100));
 * var results = repository.findAllActive(amountGreaterThan);
 * </pre>
 *
 * @param <T> the entity type extending {@link SoftDeletableEntity}
 */
public interface SoftDeleteOperations<T extends SoftDeletableEntity>
    extends JpaSpecificationExecutor<T> {

  /**
   * Creates a JPA Specification that filters for non-deleted entities.
   *
   * <p>This specification can be combined with other specifications using {@code and()} or {@code
   * or()} methods to build complex queries that also filter out soft-deleted records.
   *
   * @return a specification that matches entities where deleted = false
   */
  default Specification<T> notDeleted() {
    return (root, query, cb) -> cb.isFalse(root.get("deleted"));
  }

  /**
   * Finds all active (non-deleted) entities.
   *
   * @return a list of all entities where deleted = false
   */
  default List<T> findAllActive() {
    return findAll(notDeleted()); // Calls JpaSpecificationExecutor.findAll
  }

  /**
   * Finds all active (non-deleted) entities with pagination.
   *
   * @param pageable the pagination information (page number, size, sort)
   * @return a page of active entities
   */
  default Page<T> findAllActive(Pageable pageable) {
    return findAll(notDeleted(), pageable);
  }

  /**
   * Finds all active entities matching the given specification.
   *
   * @param spec the search criteria specification
   * @return a list of active entities matching the specification
   */
  default List<T> findAllActive(Specification<T> spec) {
    return findAll(notDeleted().and(spec));
  }

  /**
   * Finds all active entities matching the given specification with pagination.
   *
   * @param spec the search criteria specification
   * @param pageable the pagination information (page number, size, sort)
   * @return a page of active entities matching the specification
   */
  default Page<T> findAllActive(Specification<T> spec, Pageable pageable) {
    return findAll(notDeleted().and(spec), pageable);
  }

  /**
   * Finds an active entity by its ID.
   *
   * <p>This method returns an empty Optional if the entity doesn't exist or is soft-deleted.
   *
   * @param id the entity ID
   * @return an Optional containing the entity if found and active, or empty otherwise
   */
  default Optional<T> findByIdActive(Long id) {
    return findOne(notDeleted().and((root, query, cb) -> cb.equal(root.get("id"), id)));
  }

  /**
   * Finds a single active entity matching the given specification.
   *
   * @param spec the search criteria specification
   * @return an Optional containing the entity if found and active, or empty otherwise
   */
  default Optional<T> findOneActive(Specification<T> spec) {
    return findOne(notDeleted().and(spec));
  }

  /**
   * Counts active entities matching the given specification.
   *
   * @param spec the search criteria specification
   * @return the number of active entities matching the specification
   */
  default long countActive(Specification<T> spec) {
    return count(notDeleted().and(spec));
  }
}
