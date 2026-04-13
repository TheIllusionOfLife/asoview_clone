package com.asoviewclone.reservation.service;

import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.model.ReservationStatus;
import com.asoviewclone.reservation.repository.ReservationRepository;
import com.google.cloud.spanner.SpannerException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ReservationService {

  private final ReservationRepository repository;

  public ReservationService(ReservationRepository repository) {
    this.repository = repository;
  }

  public Reservation requestReservation(
      String slotId,
      String idempotencyKey,
      String guestName,
      String guestEmail,
      String consumerUserId,
      int guestCount) {
    Optional<Reservation> existing = repository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      return existing.get();
    }
    // tenantId and venueId will be resolved from the slot in later iterations.
    // For now, use placeholder values that the slot lookup will replace.
    return repository.create(
        "default-tenant", "default-venue", slotId, consumerUserId, idempotencyKey, guestName,
        guestEmail, guestCount);
  }

  public Optional<Reservation> findById(String reservationId) {
    return repository.findById(reservationId);
  }

  public List<Reservation> findByConsumerUserId(String consumerUserId) {
    return repository.findByConsumerUserId(consumerUserId);
  }

  public List<Reservation> findByVenueAndStatus(String venueId, ReservationStatus status) {
    return repository.findByVenueAndStatus(venueId, status);
  }

  public Reservation approve(String reservationId) {
    return unwrapSpannerException(() -> repository.approveAtomically(reservationId));
  }

  public Reservation reject(String reservationId, String reason) {
    return unwrapSpannerException(
        () ->
            repository.transitionStatusAtomically(
                reservationId,
                ReservationStatus.PENDING_APPROVAL,
                ReservationStatus.REJECTED,
                reason));
  }

  public Reservation waitlist(String reservationId) {
    return unwrapSpannerException(() -> repository.waitlistAtomically(reservationId));
  }

  public Reservation cancel(String reservationId, String reason) {
    return unwrapSpannerException(() -> repository.cancelAtomically(reservationId, reason));
  }

  private static <T> T unwrapSpannerException(java.util.function.Supplier<T> action) {
    try {
      return action.get();
    } catch (SpannerException e) {
      if (e.getCause() instanceof IllegalStateException ise) {
        throw ise;
      }
      throw e;
    }
  }
}
