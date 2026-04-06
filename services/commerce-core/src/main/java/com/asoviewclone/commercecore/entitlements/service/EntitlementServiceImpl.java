package com.asoviewclone.commercecore.entitlements.service;

import com.asoviewclone.commercecore.entitlements.model.Entitlement;
import com.asoviewclone.commercecore.entitlements.model.EntitlementStatus;
import com.asoviewclone.commercecore.entitlements.model.EntitlementType;
import com.asoviewclone.commercecore.entitlements.model.TicketPass;
import com.asoviewclone.commercecore.entitlements.model.TicketPassStatus;
import com.asoviewclone.commercecore.entitlements.repository.EntitlementRepository;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import com.asoviewclone.commercecore.payments.service.EntitlementCreator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class EntitlementServiceImpl implements EntitlementCreator {

  private static final Logger log = LoggerFactory.getLogger(EntitlementServiceImpl.class);

  private final EntitlementRepository entitlementRepository;
  private final QrCodeGenerator qrCodeGenerator;

  public EntitlementServiceImpl(
      EntitlementRepository entitlementRepository, QrCodeGenerator qrCodeGenerator) {
    this.entitlementRepository = entitlementRepository;
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

    // Create any missing entitlements (quantity-aware per item) plus their ticket passes.
    for (OrderItem item : order.items()) {
      long existingForItem =
          existing.stream().filter(e -> e.orderItemId().equals(item.orderItemId())).count();
      long remaining = item.quantity() - existingForItem;

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
                    null,
                    null,
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
}
