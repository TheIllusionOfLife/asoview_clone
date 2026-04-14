package com.asoviewclone.reservation.controller;

import com.asoviewclone.reservation.model.ReservationSlot;
import com.asoviewclone.reservation.service.ReservationSlotService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reservation-slots")
public class ConsumerSlotController {

  private final ReservationSlotService slotService;

  public ConsumerSlotController(ReservationSlotService slotService) {
    this.slotService = slotService;
  }

  @GetMapping
  public List<SlotAvailabilityResponse> listAvailableSlots(
      @RequestParam String venueId, @RequestParam String date) {
    return slotService.listPublicSlots(venueId, date).stream()
        .map(SlotAvailabilityResponse::from)
        .toList();
  }

  record SlotAvailabilityResponse(
      String slotId,
      String productId,
      String slotDate,
      String startTime,
      String endTime,
      long capacity,
      long approvedCount,
      long remainingCapacity) {

    static SlotAvailabilityResponse from(ReservationSlot slot) {
      return new SlotAvailabilityResponse(
          slot.slotId(),
          slot.productId(),
          slot.slotDate(),
          slot.startTime(),
          slot.endTime(),
          slot.capacity(),
          slot.approvedCount(),
          slot.capacity() - slot.approvedCount());
    }
  }
}
