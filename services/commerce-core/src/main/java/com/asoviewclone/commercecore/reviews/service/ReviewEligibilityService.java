package com.asoviewclone.commercecore.reviews.service;

import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import com.asoviewclone.commercecore.catalog.repository.ProductVariantRepository;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
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
    List<Order> orders = orderRepository.findByUserId(userId);
    Set<UUID> variantIds = new HashSet<>();
    for (Order order : orders) {
      if (order.status() != OrderStatus.PAID) {
        continue;
      }
      for (OrderItem item : order.items()) {
        try {
          variantIds.add(UUID.fromString(item.productVariantId()));
        } catch (IllegalArgumentException ignored) {
          // Skip malformed variant ids
        }
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
