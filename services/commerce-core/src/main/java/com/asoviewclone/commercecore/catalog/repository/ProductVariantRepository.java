package com.asoviewclone.commercecore.catalog.repository;

import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

  List<ProductVariant> findByProductId(UUID productId);
}
