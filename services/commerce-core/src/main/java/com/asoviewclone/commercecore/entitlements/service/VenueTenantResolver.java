package com.asoviewclone.commercecore.entitlements.service;

import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import com.asoviewclone.commercecore.catalog.repository.ProductRepository;
import com.asoviewclone.commercecore.catalog.repository.ProductVariantRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves (venueId, tenantId) for a given productVariantId by hopping ProductVariant -> Product.
 * Used by pass creation (per-order cached) and by the one-off backfill job.
 */
@Component
public class VenueTenantResolver {

  private static final Logger log = LoggerFactory.getLogger(VenueTenantResolver.class);

  private final ProductVariantRepository productVariantRepository;
  private final ProductRepository productRepository;

  public VenueTenantResolver(
      ProductVariantRepository productVariantRepository, ProductRepository productRepository) {
    this.productVariantRepository = productVariantRepository;
    this.productRepository = productRepository;
  }

  @Transactional(readOnly = true)
  public Optional<Resolution> resolve(String productVariantId) {
    if (productVariantId == null) {
      return Optional.empty();
    }
    UUID variantUuid;
    try {
      variantUuid = UUID.fromString(productVariantId);
    } catch (IllegalArgumentException e) {
      log.warn("resolve: productVariantId {} is not a UUID", productVariantId);
      return Optional.empty();
    }
    ProductVariant variant = productVariantRepository.findById(variantUuid).orElse(null);
    if (variant == null || variant.getProduct() == null) {
      return Optional.empty();
    }
    Product product =
        productRepository.findById(variant.getProduct().getId()).orElse(variant.getProduct());
    String venueId = product.getVenueId() != null ? product.getVenueId().toString() : null;
    String tenantId = product.getTenantId() != null ? product.getTenantId().toString() : null;
    if (venueId == null || tenantId == null) {
      return Optional.empty();
    }
    return Optional.of(new Resolution(venueId, tenantId));
  }

  public record Resolution(String venueId, String tenantId) {}
}
