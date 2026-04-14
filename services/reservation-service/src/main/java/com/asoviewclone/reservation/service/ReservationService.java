package com.asoviewclone.reservation.service;

import com.asoviewclone.reservation.exception.ConflictException;
import com.asoviewclone.reservation.exception.NotFoundException;
import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.model.ReservationStatus;
import com.asoviewclone.reservation.repository.ReservationRepository;
import com.asoviewclone.reservation.repository.ReservationSlotRepository;
import com.asoviewclone.reservation.security.TenantContext;
import com.google.cloud.spanner.ErrorCode;
import com.google.cloud.spanner.SpannerException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

  public List<Reservation> findByVenue(String venueId) {
    String tenantId = TenantContext.getCurrentTenantId();
    return repository.findByVenue(venueId, tenantId);
  }

  public List<Reservation> findByVenueAndStatus(String venueId, ReservationStatus status) {
    String tenantId = TenantContext.getCurrentTenantId();
    return repository.findByVenueAndStatus(venueId, status, tenantId);
  }

  public Reservation approve(String reservationId) {
    verifyTenantAccess(reservationId);
    String actorUserId = getCurrentUserId();
    return unwrapSpannerException(() -> repository.approveAtomically(reservationId, actorUserId));
  }

  public Reservation reject(String reservationId, String reason) {
    verifyTenantAccess(reservationId);
    String actorUserId = getCurrentUserId();
    return unwrapSpannerException(
        () ->
            repository.transitionStatusAtomically(
                reservationId,
                ReservationStatus.PENDING_APPROVAL,
                ReservationStatus.REJECTED,
                reason,
                actorUserId));
  }

  public Reservation waitlist(String reservationId) {
    verifyTenantAccess(reservationId);
    String actorUserId = getCurrentUserId();
    return unwrapSpannerException(() -> repository.waitlistAtomically(reservationId, actorUserId));
  }

  public Reservation cancel(String reservationId, String reason) {
    verifyTenantAccess(reservationId);
    String actorUserId = getCurrentUserId();
    return unwrapSpannerException(
        () -> repository.cancelAtomically(reservationId, reason, actorUserId));
  }

  public void verifyTenantAccess(String reservationId) {
    repository
        .findById(reservationId)
        .ifPresent(reservation -> TenantContext.requireTenant(reservation.tenantId()));
  }

  private static String getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null ? (String) auth.getPrincipal() : null;
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
      throw e;
    }
  }
}
