package com.asoviewclone.reservation.controller;

import com.asoviewclone.reservation.model.ReservationSlot;
import com.asoviewclone.reservation.service.ReservationSlotService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/op/reservation-slots")
public class ReservationSlotController {

  private final ReservationSlotService slotService;

  public ReservationSlotController(ReservationSlotService slotService) {
    this.slotService = slotService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ReservationSlot createSlot(@RequestBody CreateSlotRequest request) {
    return slotService.createSlot(
        "default-tenant",
        request.venueId(),
        request.productId(),
        request.slotDate(),
        request.startTime(),
        request.endTime(),
        request.capacity());
  }

  @GetMapping
  public List<ReservationSlot> listSlots(
      @RequestParam String venueId, @RequestParam String date) {
    return slotService.listSlots(venueId, date);
  }

  record CreateSlotRequest(
      String venueId,
      String productId,
      String slotDate,
      String startTime,
      String endTime,
      int capacity) {}
}
