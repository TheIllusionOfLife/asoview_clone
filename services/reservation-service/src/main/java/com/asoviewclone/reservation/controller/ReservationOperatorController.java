package com.asoviewclone.reservation.controller;

import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.model.ReservationStatus;
import com.asoviewclone.reservation.service.ReservationService;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReservationOperatorController {

  private final ReservationService reservationService;

  public ReservationOperatorController(ReservationService reservationService) {
    this.reservationService = reservationService;
  }

  @GetMapping("/v1/op/reservations")
  public List<Reservation> listReservations(
      @RequestParam String venueId, @RequestParam String status) {
    return reservationService.findByVenueAndStatus(
        venueId, ReservationStatus.valueOf(status));
  }

  @GetMapping("/v1/op/reservations/{id}")
  public ResponseEntity<Reservation> getReservation(@PathVariable String id) {
    return reservationService
        .findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/v1/op/reservations/{id}/approve")
  public Reservation approve(@PathVariable String id) {
    return reservationService.approve(id);
  }

  @PutMapping("/v1/op/reservations/{id}/reject")
  public Reservation reject(@PathVariable String id, @RequestBody ReasonRequest request) {
    return reservationService.reject(id, request.reason());
  }

  @PutMapping("/v1/reservations/{id}/cancel")
  public Reservation cancel(@PathVariable String id, @RequestBody ReasonRequest request) {
    return reservationService.cancel(id, request.reason());
  }

  record ReasonRequest(String reason) {}
}
