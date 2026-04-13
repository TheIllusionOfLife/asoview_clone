package com.asoviewclone.reservation.controller;

import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.service.ReservationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ReservationController {

  private final ReservationService reservationService;

  public ReservationController(ReservationService reservationService) {
    this.reservationService = reservationService;
  }

  @PostMapping("/v1/reservations")
  public ResponseEntity<Reservation> requestReservation(@RequestBody ReservationRequest request) {
    var result =
        reservationService.requestReservation(
            request.slotId(),
            request.idempotencyKey(),
            request.guestName(),
            request.guestEmail(),
            currentUserId(),
            request.guestCount());
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(result.reservation());
  }

  @GetMapping("/v1/reservations/{id}")
  public ResponseEntity<Reservation> getReservation(@PathVariable String id) {
    return reservationService
        .findById(id)
        .filter(r -> r.consumerUserId().equals(currentUserId()))
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/v1/me/reservations")
  public List<Reservation> listMyReservations() {
    return reservationService.findByConsumerUserId(currentUserId());
  }

  @PutMapping("/v1/reservations/{id}/cancel")
  public Reservation cancelReservation(
      @PathVariable String id, @RequestBody ReasonRequest request) {
    String userId = currentUserId();
    Reservation reservation =
        reservationService
            .findById(id)
            .filter(r -> r.consumerUserId().equals(userId))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
    return reservationService.cancel(reservation.reservationId(), request.reason());
  }

  private static String currentUserId() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  record ReservationRequest(
      String slotId, String idempotencyKey, String guestName, String guestEmail, int guestCount) {}

  record ReasonRequest(String reason) {}
}
