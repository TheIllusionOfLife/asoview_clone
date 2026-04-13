package com.asoviewclone.reservation.service;

import com.asoviewclone.reservation.exception.ConflictException;
import com.asoviewclone.reservation.exception.NotFoundException;
import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.model.ReservationStatus;
import com.asoviewclone.reservation.repository.ReservationRepository;
import com.asoviewclone.reservation.repository.ReservationSlotRepository;
import com.google.cloud.spanner.ErrorCode;
import com.google.cloud.spanner.SpannerException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ReservationService {

  private final ReservationRepository repository;
  private final ReservationSlotRepository slotRepository;

  public ReservationService(
      ReservationRepository repository, ReservationSlotRepository slotRepository) {
    this.repository = repository;
    this.slotRepository = slotRepository;
  }

  public record CreateResult(Reservation reservation, boolean created) {}

  public CreateResult requestReservation(
      String slotId,
      String idempotencyKey,
      String guestName,
      String guestEmail,
      String consumerUserId,
      int guestCount) {
    try {
      Reservation reservation =
          repository.createWithSlotValidation(
              slotId, consumerUserId, idempotencyKey, guestName, guestEmail, guestCount);
      return new CreateResult(reservation, true);
    } catch (SpannerException e) {
      if (e.getErrorCode() == ErrorCode.ALREADY_EXISTS) {
        Reservation existing =
            repository
                .findByConsumerUserIdAndIdempotencyKey(consumerUserId, idempotencyKey)
                .orElseThrow(
                    () ->
                        new ConflictException(
                            "Idempotency key already used by another consumer: " + idempotencyKey));
        if (!existing.slotId().equals(slotId)) {
          throw new ConflictException(
              "Idempotency key reused with different slot: " + idempotencyKey);
        }
        return new CreateResult(existing, false);
      }
      if (e.getCause() instanceof NotFoundException nfe) {
        throw nfe;
      }
      throw e;
    }
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
      if (e.getCause() instanceof NotFoundException nfe) {
        throw nfe;
      }
      if (e.getCause() instanceof ConflictException ce) {
        throw ce;
      }
      if (e.getCause() instanceof IllegalStateException ise) {
        throw ise;
      }
      throw e;
    }
  }
}
