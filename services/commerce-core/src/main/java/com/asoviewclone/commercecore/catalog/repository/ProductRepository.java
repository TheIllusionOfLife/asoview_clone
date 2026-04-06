package com.asoviewclone.commercecore.catalog.repository;

import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, UUID> {

  @Query("SELECT p FROM Product p LEFT JOIN FETCH p.variants WHERE p.id = :id")
  Optional<Product> findByIdWithVariants(UUID id);

  // Paginated queries use @BatchSize on Product.variants instead of @EntityGraph
  // to avoid Hibernate in-memory pagination (HHH000104).
  Page<Product> findByCategoryIdAndStatus(UUID categoryId, ProductStatus status, Pageable pageable);

  Page<Product> findByStatus(ProductStatus status, Pageable pageable);

  Page<Product> findByCategoryId(UUID categoryId, Pageable pageable);
}
