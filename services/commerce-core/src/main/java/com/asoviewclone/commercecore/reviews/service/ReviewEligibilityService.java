package com.asoviewclone.commercecore.reviews.service;

import com.asoviewclone.commercecore.catalog.repository.ProductVariantRepository;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-store eligibility check for writing a review: the user must have at least one PAID order
 * whose items reference a variant of the target product.
 *
 * <p>Orders live on Spanner, products/variants on Cloud SQL, so this walks both stores.
 * Pre-resolved plan decision: simple two-step lookup, no caching for correctness preservation.
 */
@Service
@Transactional(readOnly = true)
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
    // don't fetch every order in memory just to discard non-PAID rows.
    // (PR #21 review M1 from Gemini.)
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
    // Resolve variant ids → product ids in a single JPQL query rather than
    // looping over hydrated entities and walking variant.getProduct() (which
    // would have triggered a lazy-load N+1 and required an open session).
    // (PR #21 review N4 from CodeRabbit.)
    Set<UUID> productIds = productVariantRepository.findProductIdsByVariantIds(variantIds);
    return productIds.contains(productId);
  }
}
