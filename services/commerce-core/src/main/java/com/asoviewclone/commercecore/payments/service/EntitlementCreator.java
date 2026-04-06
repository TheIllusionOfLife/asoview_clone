package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.orders.model.Order;

public interface EntitlementCreator {

  void createEntitlementsForOrder(Order order);
}
