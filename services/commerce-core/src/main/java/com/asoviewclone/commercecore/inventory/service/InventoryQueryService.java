package com.asoviewclone.commercecore.inventory.service;

import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import com.asoviewclone.commercecore.catalog.service.CatalogService;
import com.asoviewclone.commercecore.inventory.model.InventorySlot;
import com.asoviewclone.commercecore.inventory.repository.InventorySlotRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side projection over {@link InventorySlotRepository} that joins live hold quantities into
 * each slot and returns a friendly {@link AvailabilityEntry} for API consumers.
 *
 * <p>Separate from {@link InventoryService} because availability is purely a query — no holds, no
 * writes — and the return shape is API-facing rather than domain-facing.
 */
@Service
public class InventoryQueryService {

  private final CatalogService catalogService;
  private final InventorySlotRepository inventorySlotRepository;

  public InventoryQueryService(
      CatalogService catalogService, InventorySlotRepository inventorySlotRepository) {
    this.catalogService = catalogService;
    this.inventorySlotRepository = inventorySlotRepository;
  }

  /**
   * Returns availability for every variant of {@code productId} in the closed date range {@code
   * [from, to]}. {@code remaining} accounts for both {@code reserved_count} and any unexpired
   * {@code inventory_holds} rows.
   */
  @Transactional(readOnly = true)
  public List<AvailabilityEntry> getProductAvailability(UUID productId, String from, String to) {
    // Collect all slots for all variants first so active-hold counts can be
    // fetched in a single Spanner query instead of one per slot (N+1).
    List<InventorySlot> allSlots = new ArrayList<>();
    Map<String, String> slotToVariant = new java.util.HashMap<>();
    for (ProductVariant variant : catalogService.getProduct(productId).getVariants()) {
      String variantId = variant.getId().toString();
      List<InventorySlot> slots = inventorySlotRepository.findAvailableSlots(variantId, from, to);
      for (InventorySlot slot : slots) {
        allSlots.add(slot);
        slotToVariant.put(slot.slotId(), variantId);
      }
    }
    if (allSlots.isEmpty()) {
      return List.of();
    }
    List<String> slotIds =
        allSlots.stream().map(InventorySlot::slotId).collect(Collectors.toList());
    Map<String, Long> holdQtyBySlot = inventorySlotRepository.countActiveHoldQuantities(slotIds);

    List<AvailabilityEntry> out = new ArrayList<>(allSlots.size());
    for (InventorySlot slot : allSlots) {
      long activeHolds = holdQtyBySlot.getOrDefault(slot.slotId(), 0L);
      long remaining = Math.max(0, slot.availableCapacity(activeHolds));
      out.add(
          new AvailabilityEntry(
              slot.slotId(),
              slotToVariant.get(slot.slotId()),
              slot.slotDate(),
              slot.startTime(),
              slot.endTime(),
              remaining));
    }
    return out;
  }

  public record AvailabilityEntry(
      String slotId,
      String productVariantId,
      String date,
      String startTime,
      String endTime,
      long remaining) {}
}
