package com.asoviewclone.commercecore.orders.controller;

import com.asoviewclone.commercecore.orders.controller.dto.CreateOrderRequest;
import com.asoviewclone.commercecore.orders.controller.dto.OrderResponse;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.service.OrderService;
import com.asoviewclone.commercecore.orders.service.OrderService.CreateOrderItemRequest;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import com.asoviewclone.common.error.ValidationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
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
    Order order = orderService.createOrder(user.userId().toString(), idempotencyKey, items);
    return OrderResponse.from(order);
  }

  @GetMapping("/me/orders")
  public List<OrderResponse> listMyOrders(@AuthenticationPrincipal AuthenticatedUser user) {
    return orderService.listUserOrders(user.userId().toString()).stream()
        .map(OrderResponse::from)
        .toList();
  }
}
