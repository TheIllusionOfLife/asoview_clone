package com.asoviewclone.reservation.controller;

import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.service.ReservationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReservationController {

  private final ReservationService reservationService;

  public ReservationController(ReservationService reservationService) {
    this.reservationService = reservationService;
  }

  @PostMapping("/v1/reservations")
  public ResponseEntity<Reservation> requestReservation(@RequestBody ReservationRequest request) {
    Reservation reservation =
        reservationService.requestReservation(
            request.slotId(),
            request.idempotencyKey(),
            request.guestName(),
            request.guestEmail(),
            "anonymous",
            request.guestCount());
    return ResponseEntity.status(HttpStatus.CREATED).body(reservation);
  }

  @GetMapping("/v1/reservations/{id}")
  public ResponseEntity<Reservation> getReservation(@PathVariable String id) {
    return reservationService
        .findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/v1/me/reservations")
  public List<Reservation> listMyReservations() {
    // userId will come from security context in later iterations
    return reservationService.findByConsumerUserId("anonymous");
  }

  record ReservationRequest(
      String slotId,
      String idempotencyKey,
      String guestName,
      String guestEmail,
      int guestCount) {}
}
