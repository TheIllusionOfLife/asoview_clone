package com.asoviewclone.commercecore.inventory.service;

import com.asoviewclone.commercecore.inventory.model.InventoryHold;
import com.asoviewclone.commercecore.inventory.model.InventorySlot;
import com.asoviewclone.commercecore.inventory.repository.InventorySlotRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InventoryServiceImpl implements InventoryService {

  private final InventorySlotRepository inventorySlotRepository;

  public InventoryServiceImpl(InventorySlotRepository inventorySlotRepository) {
    this.inventorySlotRepository = inventorySlotRepository;
  }

  @Override
  public List<InventorySlot> getAvailableSlots(
      String productVariantId, String startDate, String endDate) {
    return inventorySlotRepository.findAvailableSlots(productVariantId, startDate, endDate);
  }

  @Override
  public InventoryHold holdInventory(String slotId, String userId, int quantity) {
    return inventorySlotRepository.holdInventory(slotId, userId, quantity);
  }

  @Override
  public void confirmHold(String holdId) {
    inventorySlotRepository.confirmHold(holdId);
  }

  @Override
  public void releaseHold(String holdId) {
    inventorySlotRepository.releaseHold(holdId);
  }
}
