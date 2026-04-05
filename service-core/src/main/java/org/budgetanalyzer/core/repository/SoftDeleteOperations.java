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
 * SoftDeletableEntity}. It provides non-deleted versions of common query methods that exclude
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
 * var allNotDeleted = repository.findAllNotDeleted();
 * var transaction = repository.findByIdNotDeleted(id)
 *     .orElseThrow(() -&gt; new ResourceNotFoundException("Transaction not found"));
 *
 * var notDeletedPage = repository.findAllNotDeleted(PageRequest.of(0, 10));
 *
 * // With custom specifications
 * Specification&lt;Transaction&gt; amountGreaterThan = (root, query, cb) -&gt;
 *     cb.greaterThan(root.get("amount"), BigDecimal.valueOf(100));
 * var results = repository.findAllNotDeleted(amountGreaterThan);
 * </pre>
 *
 * @param <T> the entity type extending {@link SoftDeletableEntity}
 * @param <ID> the entity identifier type
 */
public interface SoftDeleteOperations<T extends SoftDeletableEntity, ID>
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
   * Finds all non-deleted entities.
   *
   * @return a list of all entities where deleted = false
   */
  default List<T> findAllNotDeleted() {
    return findAll(notDeleted()); // Calls JpaSpecificationExecutor.findAll
  }

  /**
   * Finds all non-deleted entities with pagination.
   *
   * @param pageable the pagination information (page number, size, sort)
   * @return a page of non-deleted entities
   */
  default Page<T> findAllNotDeleted(Pageable pageable) {
    return findAll(notDeleted(), pageable);
  }

  /**
   * Finds all non-deleted entities matching the given specification.
   *
   * @param spec the search criteria specification
   * @return a list of non-deleted entities matching the specification
   */
  default List<T> findAllNotDeleted(Specification<T> spec) {
    return findAll(notDeleted().and(spec));
  }

  /**
   * Finds all non-deleted entities matching the given specification with pagination.
   *
   * @param spec the search criteria specification
   * @param pageable the pagination information (page number, size, sort)
   * @return a page of non-deleted entities matching the specification
   */
  default Page<T> findAllNotDeleted(Specification<T> spec, Pageable pageable) {
    return findAll(notDeleted().and(spec), pageable);
  }

  /**
   * Finds a non-deleted entity by its ID.
   *
   * <p>This method returns an empty Optional if the entity doesn't exist or is soft-deleted.
   *
   * @param id the entity ID
   * @return an Optional containing the entity if found and not deleted, or empty otherwise
   */
  default Optional<T> findByIdNotDeleted(ID id) {
    return findOne(notDeleted().and((root, query, cb) -> cb.equal(root.get("id"), id)));
  }

  /**
   * Finds a single non-deleted entity matching the given specification.
   *
   * @param spec the search criteria specification
   * @return an Optional containing the entity if found and not deleted, or empty otherwise
   */
  default Optional<T> findOneNotDeleted(Specification<T> spec) {
    return findOne(notDeleted().and(spec));
  }

  /**
   * Counts non-deleted entities matching the given specification.
   *
   * @param spec the search criteria specification
   * @return the number of non-deleted entities matching the specification
   */
  default long countNotDeleted(Specification<T> spec) {
    return count(notDeleted().and(spec));
  }
}
