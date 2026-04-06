package com.asoviewclone.commercecore.entitlements.service;

import com.asoviewclone.commercecore.entitlements.model.Entitlement;
import com.asoviewclone.commercecore.entitlements.model.EntitlementStatus;
import com.asoviewclone.commercecore.entitlements.model.EntitlementType;
import com.asoviewclone.commercecore.entitlements.model.TicketPass;
import com.asoviewclone.commercecore.entitlements.model.TicketPassStatus;
import com.asoviewclone.commercecore.entitlements.model.TicketPassView;
import com.asoviewclone.commercecore.entitlements.repository.EntitlementRepository;
import com.asoviewclone.commercecore.inventory.model.InventorySlot;
import com.asoviewclone.commercecore.inventory.repository.InventorySlotRepository;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import com.asoviewclone.commercecore.payments.service.EntitlementCreator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class EntitlementServiceImpl implements EntitlementCreator {

  private static final Logger log = LoggerFactory.getLogger(EntitlementServiceImpl.class);

  /**
   * Asoview is a Japanese marketplace; slot dates and start/end times are stored as wall-clock
   * strings without a timezone. Anchor them to JST when projecting to UTC instants for the
   * entitlement validity window.
   */
  private static final ZoneId VENUE_ZONE = ZoneId.of("Asia/Tokyo");

  private final EntitlementRepository entitlementRepository;
  private final InventorySlotRepository inventorySlotRepository;
  private final QrCodeGenerator qrCodeGenerator;

  public EntitlementServiceImpl(
      EntitlementRepository entitlementRepository,
      InventorySlotRepository inventorySlotRepository,
      QrCodeGenerator qrCodeGenerator) {
    this.entitlementRepository = entitlementRepository;
    this.inventorySlotRepository = inventorySlotRepository;
    this.qrCodeGenerator = qrCodeGenerator;
  }

  @Override
  public void createEntitlementsForOrder(Order order) {
    // Repair any existing entitlements that are missing ticket passes (partial
    // creation from a previous failed attempt).
    List<Entitlement> existing = entitlementRepository.findByOrderId(order.orderId());
    for (Entitlement e : existing) {
      if (entitlementRepository.findTicketPassesByEntitlementId(e.entitlementId()).isEmpty()) {
        entitlementRepository.saveTicketPass(
            new TicketPass(
                null,
                e.entitlementId(),
                qrCodeGenerator.generate(),
                TicketPassStatus.VALID,
                null,
                null));
      }
    }

    // Cache slot lookups across items so multi-quantity orders don't re-query the same row.
    Map<String, InventorySlot> slotCache = new HashMap<>();

    // Create any missing entitlements (quantity-aware per item) plus their ticket passes.
    for (OrderItem item : order.items()) {
      long existingForItem =
          existing.stream().filter(e -> e.orderItemId().equals(item.orderItemId())).count();
      long remaining = item.quantity() - existingForItem;

      // Resolve the slot's wall-clock window into UTC instants once per item. The slot is
      // expected to exist (the order references it) but defend against null so a corrupt slot
      // id never crashes payment confirmation — entitlements without validity are still usable
      // by the QR scanner, the frontend just won't gate the QR display on a window.
      InventorySlot slot =
          slotCache.computeIfAbsent(item.slotId(), inventorySlotRepository::findById);
      Instant validFrom = null;
      Instant validUntil = null;
      if (slot != null && slot.slotDate() != null) {
        LocalDate date = LocalDate.parse(slot.slotDate());
        if (slot.startTime() != null) {
          validFrom = date.atTime(LocalTime.parse(slot.startTime())).atZone(VENUE_ZONE).toInstant();
        }
        if (slot.endTime() != null) {
          validUntil = date.atTime(LocalTime.parse(slot.endTime())).atZone(VENUE_ZONE).toInstant();
        }
      }

      for (long i = 0; i < remaining; i++) {
        Entitlement entitlement =
            entitlementRepository.save(
                new Entitlement(
                    null,
                    order.orderId(),
                    item.orderItemId(),
                    order.userId(),
                    item.productVariantId(),
                    EntitlementType.TICKET,
                    EntitlementStatus.ACTIVE,
                    validFrom,
                    validUntil,
                    null));

        entitlementRepository.saveTicketPass(
            new TicketPass(
                null,
                entitlement.entitlementId(),
                qrCodeGenerator.generate(),
                TicketPassStatus.VALID,
                null,
                null));
      }
    }
    log.info("Created entitlements for order {}", order.orderId());
  }

  public List<Entitlement> listUserEntitlements(String userId) {
    return entitlementRepository.findByUserId(userId);
  }

  public List<TicketPass> listUserTicketPasses(String userId) {
    return entitlementRepository.findTicketPassesByUserId(userId);
  }

  /**
   * Returns ticket passes joined with their parent entitlement so the consumer web app's
   * /tickets/[orderId] page has the order id and validity window in a single response. When {@code
   * orderIdOrNull} is non-null the result is filtered to that order.
   */
  public List<TicketPassView> listUserTicketPassViews(String userId, String orderIdOrNull) {
    return entitlementRepository.findTicketPassViewsByUserId(userId, orderIdOrNull);
  }
}
