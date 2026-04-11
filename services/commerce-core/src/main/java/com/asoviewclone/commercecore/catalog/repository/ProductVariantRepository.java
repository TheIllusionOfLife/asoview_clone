package com.asoviewclone.commercecore.catalog.repository;

import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

  List<ProductVariant> findByProductId(UUID productId);

  /**
   * Returns the set of {@code product_id} values referenced by the given variant ids in a single
   * query. Avoids the per-variant {@code variant.getProduct().getId()} lazy load that the
   * straightforward {@link #findAllById(Iterable)} loop triggers when a caller only needs the
   * parent product ids. Used by {@code ReviewEligibilityService}.
   */
  @Query("SELECT DISTINCT pv.product.id FROM ProductVariant pv WHERE pv.id IN :variantIds")
  Set<UUID> findProductIdsByVariantIds(@Param("variantIds") Collection<UUID> variantIds);

  /**
   * Returns variant-to-product id pairs for the given variant ids in a single JPQL query. Avoids
   * the {@code LazyInitializationException} that {@code findAllById + getProduct().getId()}
   * triggers when {@code open-in-view=false} and the caller is outside a transaction (e.g. a
   * controller).
   */
  @Query("SELECT pv.id, pv.product.id FROM ProductVariant pv WHERE pv.id IN :variantIds")
  List<Object[]> findVariantProductPairs(@Param("variantIds") Collection<UUID> variantIds);
}
