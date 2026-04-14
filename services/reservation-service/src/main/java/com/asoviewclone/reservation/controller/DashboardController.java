package com.asoviewclone.reservation.controller;

import com.asoviewclone.reservation.model.SlotUtilization;
import com.asoviewclone.reservation.repository.ReservationRepository;
import com.asoviewclone.reservation.repository.ReservationSlotRepository;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

  private final ReservationRepository reservationRepository;
  private final ReservationSlotRepository slotRepository;

  public DashboardController(
      ReservationRepository reservationRepository, ReservationSlotRepository slotRepository) {
    this.reservationRepository = reservationRepository;
    this.slotRepository = slotRepository;
  }

  @GetMapping("/v1/op/dashboard")
  public DashboardSummary getDashboard(@RequestParam String venueId) {
    Map<String, Long> counts = reservationRepository.countByStatus(venueId);
    SlotUtilization utilization = slotRepository.getUtilization(venueId);
    return new DashboardSummary(counts, utilization);
  }

  @GetMapping("/v1/op/me/venues")
  public List<String> getMyVenues() {
    return slotRepository.findDistinctVenueIds();
  }

  public record DashboardSummary(
      Map<String, Long> reservationCounts, SlotUtilization slotUtilization) {}
}
