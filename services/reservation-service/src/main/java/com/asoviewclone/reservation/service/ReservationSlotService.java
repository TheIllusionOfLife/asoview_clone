package com.asoviewclone.reservation.service;

import com.asoviewclone.reservation.model.ReservationSlot;
import com.asoviewclone.reservation.repository.ReservationSlotRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReservationSlotService {

  private final ReservationSlotRepository repository;

  public ReservationSlotService(ReservationSlotRepository repository) {
    this.repository = repository;
  }

  public ReservationSlot createSlot(
      String tenantId,
      String venueId,
      String productId,
      String slotDate,
      String startTime,
      String endTime,
      int capacity) {
    return repository.create(tenantId, venueId, productId, slotDate, startTime, endTime, capacity);
  }

  public List<ReservationSlot> listSlots(String venueId, String date) {
    return repository.findByVenueAndDate(venueId, date);
  }
}
