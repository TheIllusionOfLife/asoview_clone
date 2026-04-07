package com.asoviewclone.commercecore.orders.service;

import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import com.asoviewclone.commercecore.catalog.repository.ProductVariantRepository;
import com.asoviewclone.commercecore.inventory.model.InventoryHold;
import com.asoviewclone.commercecore.inventory.service.InventoryService;
import com.asoviewclone.commercecore.orders.event.OrderCancelledEvent;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import com.asoviewclone.commercecore.points.discount.OrderDiscountService;
import com.asoviewclone.common.error.ConflictException;
import com.asoviewclone.common.error.NotFoundException;
import com.asoviewclone.common.error.ValidationException;
import com.google.cloud.spanner.SpannerException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

  /**
   * Internal marker exception thrown by {@code createOrder} when a Spanner write fails AFTER the
   * points-burn was committed AND the burn-refund itself fails. The user's points are stranded
   * against an order id that may never exist; we surface this as a 500 to the caller (instead of
   * silently returning a race-winner order) so ops can repair the orphan ledger row.
   */
  public static class StrandedPointsBurnException extends RuntimeException {
    public StrandedPointsBurnException(String orderId, Throwable cause) {
      super("Stranded points burn against order " + orderId + " — manual repair required", cause);
    }
  }

  private final OrderRepository orderRepository;
  private final InventoryService inventoryService;
  private final ProductVariantRepository productVariantRepository;
  private final OrderDiscountService orderDiscountService;
  private final ApplicationEventPublisher eventPublisher;

  public OrderServiceImpl(
      OrderRepository orderRepository,
      InventoryService inventoryService,
      ProductVariantRepository productVariantRepository,
      OrderDiscountService orderDiscountService,
      ApplicationEventPublisher eventPublisher) {
    this.orderRepository = orderRepository;
    this.inventoryService = inventoryService;
    this.productVariantRepository = productVariantRepository;
    this.orderDiscountService = orderDiscountService;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public Order createOrder(
      String userId, String idempotencyKey, List<CreateOrderItemRequest> items, long pointsToUse) {
    if (items == null || items.isEmpty()) {
      throw new ValidationException("Order must have at least one item");
    }

    // Idempotency check: verify the existing order belongs to the same user
    Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      if (!existing.get().userId().equals(userId)) {
        throw new ValidationException("Idempotency key already used by another user");
      }
      return existing.get();
    }

    // Hold inventory, look up prices, and save order.
    // If anything fails after holds are created, release them all.
    List<InventoryHold> holds = new ArrayList<>();
    try {
      // Hold inventory for each item
      for (CreateOrderItemRequest item : items) {
        InventoryHold hold = inventoryService.holdInventory(item.slotId(), userId, item.quantity());
        // Record the hold immediately so the catch block's best-effort release path can
        // clean it up if validation below throws.
        holds.add(hold);
        // Validate the slot belongs to the requested product variant. The repository
        // populates the hold's productVariantId from the slot row, so a mismatch means
        // the client referenced a slot that does not belong to their variant.
        if (!hold.productVariantId().equals(item.productVariantId())) {
          throw new ValidationException("Slot does not belong to requested product variant");
        }
      }

      // Look up variant prices from catalog and calculate total
      BigDecimal total = BigDecimal.ZERO;
      String currency = null;
      List<OrderItem> orderItems = new ArrayList<>();
      for (int i = 0; i < items.size(); i++) {
        CreateOrderItemRequest item = items.get(i);
        InventoryHold hold = holds.get(i);

        ProductVariant variant =
            productVariantRepository
                .findById(UUID.fromString(item.productVariantId()))
                .orElseThrow(
                    () -> new NotFoundException("ProductVariant", item.productVariantId()));

        // Derive and validate currency from variant
        if (currency == null) {
          currency = variant.getPriceCurrency();
        } else if (!currency.equals(variant.getPriceCurrency())) {
          throw new ValidationException(
              "Mixed currencies in order: " + currency + " and " + variant.getPriceCurrency());
        }

        String unitPrice = variant.getPriceAmount().toPlainString();
        BigDecimal itemTotal =
            variant.getPriceAmount().multiply(BigDecimal.valueOf(item.quantity()));
        total = total.add(itemTotal);

        orderItems.add(
            new OrderItem(
                null,
                null,
                item.productVariantId(),
                item.slotId(),
                item.quantity(),
                unitPrice,
                hold.holdId(),
                null));
      }

      // Burn points (and validate balance) BEFORE writing the Spanner order. If
      // burn fails after the Spanner write, the order row stays as PENDING with
      // released holds and poisons idempotency on retry — the next request with
      // the same key would findByIdempotencyKey, hit the orphan, and proceed to
      // payment with non-existent holds. (PR #21 review C4 from Devin.)
      //
      // Pre-generate the order id so the points-burn ledger row can pin to a
      // stable order id; OrderRepository.save accepts the pre-generated id.
      //
      // PROCESS-CRASH RISK: if the JVM dies between the points burn (Postgres
      // commit) and orderRepository.saveWithId (Spanner write), the burn is
      // stranded against an order id that never makes it to Spanner. This is
      // the well-known cross-store outbox problem. The reconciliation sweep
      // in OrphanedDiscountReconciliationJob (scheduled) sweeps Postgres
      // discount rows whose order_id has no matching Spanner row and refunds
      // them. Codex review flagged this as a design recommendation; full
      // outbox/saga refactor is tracked as a follow-up but the recovery sweep
      // already prevents permanent loss in the crash-mid-method case.
      String preGeneratedOrderId = UUID.randomUUID().toString();
      if (pointsToUse > 0) {
        orderDiscountService.applyPointsBurnDiscount(
            preGeneratedOrderId, UUID.fromString(userId), pointsToUse, total);
      }

      // CRITICAL: subtract burned points from the order's payable total so the
      // payment intent is created for the discounted amount, not the gross total.
      // Without this the user loses points AND is charged the full amount —
      // a silent overcharge bug. (PR #21 Codex finding.)
      BigDecimal payableTotal = total;
      if (pointsToUse > 0) {
        payableTotal = total.subtract(BigDecimal.valueOf(pointsToUse)).max(BigDecimal.ZERO);
      }

      Order saved;
      try {
        saved =
            orderRepository.saveWithId(
                preGeneratedOrderId,
                userId,
                payableTotal.toPlainString(),
                currency,
                idempotencyKey,
                orderItems);
      } catch (RuntimeException ex) {
        // Spanner write failed after the burn committed. Roll the burn back so
        // we don't trap the user's points behind a non-existent order.
        // CRITICAL: if the refund itself fails, do NOT silently swallow it —
        // the points are now stranded against an order id that may never
        // exist (the ALREADY_EXISTS race-winner path below would otherwise
        // return success and the user's points would be lost). Re-throw a
        // marker exception so the outer SpannerException handler refuses to
        // return the race winner. (PR #21 review follow-up from Devin.)
        if (pointsToUse > 0) {
          try {
            orderDiscountService.refundForCancelledOrder(
                preGeneratedOrderId, UUID.fromString(userId));
          } catch (Exception refundEx) {
            log.error(
                "STRANDED POINTS BURN: order_id={} user={} pointsToUse={}; manual repair required",
                preGeneratedOrderId,
                userId,
                pointsToUse,
                refundEx);
            // Wrap the original Spanner failure with a marker so the outer
            // SpannerException handler can detect the stranded-burn case and
            // surface a 500 instead of returning the race-winner.
            throw new StrandedPointsBurnException(preGeneratedOrderId, ex);
          }
        }
        throw ex;
      }
      return saved;
    } catch (StrandedPointsBurnException stranded) {
      // The points-burn refund failed after a Spanner write failure. Holds
      // released, but the burn is permanent until ops repairs the orphan
      // ledger row. Surface as a 500 — DO NOT return any race-winner order
      // or the caller will think their points were used cleanly.
      releaseHoldsBestEffort(holds);
      throw stranded;
    } catch (SpannerException e) {
      // Handle idempotency race: concurrent insert with same idempotency key
      if (e.getErrorCode() == com.google.cloud.spanner.ErrorCode.ALREADY_EXISTS) {
        Optional<Order> raceWinner = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (raceWinner.isPresent() && raceWinner.get().userId().equals(userId)) {
          releaseHoldsBestEffort(holds);
          return raceWinner.get();
        }
      }
      releaseHoldsBestEffort(holds);
      throw e;
    } catch (Exception e) {
      releaseHoldsBestEffort(holds);
      throw e;
    }
  }

  /**
   * Best-effort hold release used by every cleanup branch in {@code createOrder}. Each release runs
   * in its own try/catch so a failure on one hold does not prevent the rest from being released.
   * (PR #21 review N — extracted helper for the repeated cleanup loop.)
   */
  private void releaseHoldsBestEffort(List<InventoryHold> holds) {
    for (InventoryHold hold : holds) {
      try {
        inventoryService.releaseHold(hold.holdId());
      } catch (Exception ignored) {
        // Best-effort: hold may have already expired or been confirmed.
      }
    }
  }

  @Override
  public Order getOrder(String orderId) {
    return orderRepository.findById(orderId);
  }

  @Override
  public List<Order> listUserOrders(String userId) {
    return orderRepository.findByUserId(userId);
  }

  @Override
  @org.springframework.transaction.annotation.Transactional
  public void cancelOrder(String orderId) {
    // @Transactional is required even though every persistent write here is a
    // Spanner CAS (Spanner runs its own tx). The empty JPA transaction is the
    // hook @TransactionalEventListener(AFTER_COMMIT) needs — without it,
    // PointRefundListener silently never fires and burned points are never
    // refunded on cancel. (PR #21 review C1 from Devin.)
    Order order = orderRepository.findById(orderId);
    OrderStatus currentStatus = order.status();
    if (!currentStatus.canTransitionTo(OrderStatus.CANCELLED)) {
      throw new ValidationException("Cannot cancel order in status " + currentStatus);
    }

    // Compare-and-swap FIRST: only proceed to release holds if we won the race.
    // Releasing holds before the CAS would leak inventory if a concurrent confirm
    // (PAYMENT_PENDING -> PAID) wins, because the holds would be gone but the
    // saga still believes they exist.
    boolean swapped = orderRepository.updateStatusIf(orderId, currentStatus, OrderStatus.CANCELLED);
    if (!swapped) {
      throw new ConflictException(
          "Order " + orderId + " status changed concurrently; cancel aborted");
    }

    // Release inventory holds for each item now that the cancel is durable.
    for (OrderItem item : order.items()) {
      if (item.holdId() != null) {
        try {
          inventoryService.releaseHold(item.holdId());
        } catch (Exception ignored) {
          // Best effort: hold may have already expired
        }
      }
    }

    // Publish OrderCancelledEvent so points-refund and other side effects can run
    // AFTER_COMMIT. Spanner CAS is already durable, so listeners do not need the
    // local transaction; they subscribe via @TransactionalEventListener(AFTER_COMMIT).
    eventPublisher.publishEvent(new OrderCancelledEvent(orderId, order.userId()));
  }
}
