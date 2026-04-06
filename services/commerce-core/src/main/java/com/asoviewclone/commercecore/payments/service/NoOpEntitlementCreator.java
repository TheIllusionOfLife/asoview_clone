package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.orders.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * No-op implementation until entitlements domain is wired. Replaced by EntitlementServiceImpl in
 * the entitlements commit.
 */
@Component
public class NoOpEntitlementCreator implements EntitlementCreator {

  private static final Logger log = LoggerFactory.getLogger(NoOpEntitlementCreator.class);

  @Override
  public void createEntitlementsForOrder(Order order) {
    log.info("Entitlement creation not yet implemented for order {}", order.orderId());
  }
}
