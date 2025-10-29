package com.bleurubin.core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.bleurubin.core.domain.SoftDeletable;

public interface SoftDeleteOperations<T extends SoftDeletable> extends JpaSpecificationExecutor<T> {

  default Specification<T> notDeleted() {
    return (root, query, cb) -> cb.isFalse(root.get("softDelete").get("deleted"));
  }

  default List<T> findAllActive() {
    return findAll(notDeleted()); // Calls JpaSpecificationExecutor.findAll
  }

  default Page<T> findAllActive(Pageable pageable) {
    return findAll(notDeleted(), pageable);
  }

  default Optional<T> findByIdActive(Long id) {
    return findOne(notDeleted().and((root, query, cb) -> cb.equal(root.get("id"), id)));
  }

  default List<T> findAllActive(Specification<T> spec) {
    return findAll(notDeleted().and(spec));
  }

  default Page<T> findAllActive(Specification<T> spec, Pageable pageable) {
    return findAll(notDeleted().and(spec), pageable);
  }

  default Optional<T> findOneActive(Specification<T> spec) {
    return findOne(notDeleted().and(spec));
  }

  default long countActive(Specification<T> spec) {
    return count(notDeleted().and(spec));
  }
}
