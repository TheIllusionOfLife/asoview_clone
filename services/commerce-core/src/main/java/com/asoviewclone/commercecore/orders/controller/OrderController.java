package com.asoviewclone.commercecore.orders.controller;

import com.asoviewclone.commercecore.catalog.repository.ProductVariantRepository;
import com.asoviewclone.commercecore.orders.controller.dto.CreateOrderRequest;
import com.asoviewclone.commercecore.orders.controller.dto.OrderResponse;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.service.OrderService;
import com.asoviewclone.commercecore.orders.service.OrderService.CreateOrderItemRequest;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import com.asoviewclone.common.error.NotFoundException;
import com.asoviewclone.common.error.ValidationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class OrderController {

  private final OrderService orderService;
  private final ProductVariantRepository productVariantRepository;

  public OrderController(
      OrderService orderService, ProductVariantRepository productVariantRepository) {
    this.orderService = orderService;
    this.productVariantRepository = productVariantRepository;
  }

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public OrderResponse createOrder(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
      @RequestBody CreateOrderRequest request) {
    // Header takes precedence over the legacy body field so browser clients can stay
    // aligned with the standard Idempotency-Key convention and still interop with older
    // callers that pass it in the body. Trim whitespace so "abc" and " abc " cannot
    // become distinct dedupe keys.
    String headerTrimmed = idempotencyHeader != null ? idempotencyHeader.trim() : null;
    String bodyTrimmed = request.idempotencyKey() != null ? request.idempotencyKey().trim() : null;
    String idempotencyKey =
        headerTrimmed != null && !headerTrimmed.isEmpty() ? headerTrimmed : bodyTrimmed;
    if (idempotencyKey == null || idempotencyKey.isEmpty()) {
      throw new ValidationException("Idempotency-Key header or idempotencyKey body field required");
    }
    List<CreateOrderItemRequest> items =
        request.items().stream()
            .map(i -> new CreateOrderItemRequest(i.productVariantId(), i.slotId(), i.quantity()))
            .toList();
    long pointsToUse = request.pointsToUse() != null ? Math.max(0, request.pointsToUse()) : 0L;
    Order order =
        orderService.createOrder(user.userId().toString(), idempotencyKey, items, pointsToUse);
    return toResponse(order);
  }

  /**
   * Single-order lookup. Used by the consumer web app's checkout polling loop and ticket page.
   * Returns 404 if the order does not exist OR belongs to a different user — never 403, so we don't
   * leak existence of orders the caller doesn't own.
   */
  @GetMapping("/orders/{orderId}")
  public OrderResponse getOrder(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String orderId) {
    // orderService.getOrder already throws NotFoundException with the right shape;
    // let it propagate. Only the cross-user case needs an explicit throw.
    Order order = orderService.getOrder(orderId);
    if (!user.userId().toString().equals(order.userId())) {
      throw new NotFoundException("Order", orderId);
    }
    return toResponse(order);
  }

  @GetMapping("/me/orders")
  public List<OrderResponse> listMyOrders(@AuthenticationPrincipal AuthenticatedUser user) {
    List<Order> orders = orderService.listUserOrders(user.userId().toString());
    // Batch-fetch all variant->product mappings across all orders in one query.
    List<UUID> allVariantIds =
        orders.stream()
            .flatMap(o -> o.items().stream())
            .map(item -> UUID.fromString(item.productVariantId()))
            .distinct()
            .toList();
    Map<UUID, UUID> variantToProduct = resolveVariantToProductMap(allVariantIds);
    return orders.stream().map(o -> OrderResponse.from(o, variantToProduct)).toList();
  }

  private OrderResponse toResponse(Order order) {
    List<UUID> variantIds =
        order.items().stream()
            .map(item -> UUID.fromString(item.productVariantId()))
            .distinct()
            .toList();
    Map<UUID, UUID> variantToProduct = resolveVariantToProductMap(variantIds);
    return OrderResponse.from(order, variantToProduct);
  }

  /**
   * Resolves variant ids to their parent product ids in a single batch JPQL query. Returns an empty
   * map when the input is empty to avoid sending an empty IN clause. Uses a projection query to
   * avoid LazyInitializationException (open-in-view=false, controller is not @Transactional).
   */
  private Map<UUID, UUID> resolveVariantToProductMap(List<UUID> variantIds) {
    if (variantIds.isEmpty()) {
      return Map.of();
    }
    return productVariantRepository.findVariantProductPairs(variantIds).stream()
        .collect(Collectors.toMap(row -> (UUID) row[0], row -> (UUID) row[1]));
  }
}
