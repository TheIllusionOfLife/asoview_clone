package com.asoviewclone.reservation.service;

import com.asoviewclone.reservation.exception.ConflictException;
import com.asoviewclone.reservation.exception.NotFoundException;
import com.asoviewclone.reservation.model.ReservationSlot;
import com.asoviewclone.reservation.repository.ReservationSlotRepository;
import com.asoviewclone.reservation.security.TenantContext;
import com.google.cloud.spanner.SpannerException;
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
    String tenantId = TenantContext.getCurrentTenantId();
    return repository.findByVenueAndDate(venueId, date, tenantId);
  }

  public List<ReservationSlot> listPublicSlots(String venueId, String date) {
    return repository.findByVenueAndDate(venueId, date, null);
  }

  public ReservationSlot updateSlot(
      String slotId, String startTime, String endTime, long capacity) {
    String tenantId = TenantContext.getCurrentTenantId();
    return unwrapSpannerException(
        () -> repository.updateSlot(slotId, tenantId, startTime, endTime, capacity));
  }

  public void deleteSlot(String slotId) {
    String tenantId = TenantContext.getCurrentTenantId();
    unwrapSpannerException(
        () -> {
          repository.deleteSlot(slotId, tenantId);
          return null;
        });
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
