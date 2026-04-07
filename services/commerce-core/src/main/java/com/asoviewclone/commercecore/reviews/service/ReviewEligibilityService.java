package com.asoviewclone.commercecore.reviews.service;

import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import com.asoviewclone.commercecore.catalog.repository.ProductVariantRepository;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Cross-store eligibility check for writing a review: the user must have at least one PAID order
 * whose items reference a variant of the target product.
 *
 * <p>Orders live on Spanner, products/variants on Cloud SQL, so this walks both stores.
 * Pre-resolved plan decision: simple two-step lookup, no caching for correctness preservation.
 */
@Service
public class ReviewEligibilityService {

  private final OrderRepository orderRepository;
  private final ProductVariantRepository productVariantRepository;

  public ReviewEligibilityService(
      OrderRepository orderRepository, ProductVariantRepository productVariantRepository) {
    this.orderRepository = orderRepository;
    this.productVariantRepository = productVariantRepository;
  }

  public boolean existsPaidOrderForUserAndProduct(String userId, UUID productId) {
    // Push the user filter AND status=PAID filter into the Spanner query so we
    // don't fetch every order in memory just to discard non-PAID rows. Returns
    // distinct variant ids only. (PR #21 review M1 from Gemini.)
    Set<String> rawVariantIds = orderRepository.findPaidVariantIdsByUserId(userId);
    Set<UUID> variantIds = new HashSet<>();
    for (String raw : rawVariantIds) {
      try {
        variantIds.add(UUID.fromString(raw));
      } catch (IllegalArgumentException ignored) {
        // Skip malformed variant ids
      }
    }
    if (variantIds.isEmpty()) {
      return false;
    }
    List<ProductVariant> variants =
        productVariantRepository.findAllById(new ArrayList<>(variantIds));
    for (ProductVariant variant : variants) {
      if (variant.getProduct() != null && productId.equals(variant.getProduct().getId())) {
        return true;
      }
    }
    return false;
  }
}
