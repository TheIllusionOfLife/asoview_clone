package com.asoviewclone.commercecore.inventory.service;

import com.asoviewclone.commercecore.inventory.model.InventoryHold;
import com.asoviewclone.commercecore.inventory.model.InventorySlot;
import java.util.List;

public interface InventoryService {

  List<InventorySlot> getAvailableSlots(String productVariantId, String startDate, String endDate);

  InventoryHold holdInventory(String slotId, String userId, int quantity);

  void confirmHold(String holdId);

  void releaseHold(String holdId);
}
