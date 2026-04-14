package com.asoviewclone.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.reservation.model.AuditLog;
import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.model.ReservationSlot;
import com.asoviewclone.reservation.repository.AuditLogRepository;
import com.asoviewclone.reservation.repository.ReservationRepository;
import com.asoviewclone.reservation.repository.ReservationSlotRepository;
import com.asoviewclone.reservation.testutil.SpannerEmulatorConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(SpannerEmulatorConfig.class)
@ActiveProfiles("test")
class AuditLogTest {

  @Autowired private ReservationService reservationService;
  @Autowired private ReservationSlotRepository slotRepository;
  @Autowired private ReservationRepository reservationRepository;
  @Autowired private AuditLogRepository auditLogRepository;

  @BeforeEach
  void cleanup() {
    reservationRepository.deleteAll();
    slotRepository.deleteAll();
  }

  @Test
  void createReservation_writesCreatedAuditLog() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.createWithSlotValidation(
            slot.slotId(), "u-1", "idem-audit-1", "Taro", "t@e.com", 2);

    List<AuditLog> logs = auditLogRepository.findByReservationId(reservation.reservationId());
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).action()).isEqualTo("CREATED");
    assertThat(logs.get(0).actorUserId()).isEqualTo("u-1");
  }

  @Test
  void approve_writesApprovedAuditLog() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.createWithSlotValidation(
            slot.slotId(), "u-1", "idem-audit-2", "Taro", "t@e.com", 2);

    reservationService.approve(reservation.reservationId());

    List<AuditLog> logs = auditLogRepository.findByReservationId(reservation.reservationId());
    assertThat(logs).hasSize(2);
    assertThat(logs.get(0).action()).isEqualTo("CREATED");
    assertThat(logs.get(1).action()).isEqualTo("APPROVED");
  }

  @Test
  void reject_writesRejectedAuditLogWithReason() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.createWithSlotValidation(
            slot.slotId(), "u-1", "idem-audit-3", "Taro", "t@e.com", 1);

    reservationService.reject(reservation.reservationId(), "Not suitable");

    List<AuditLog> logs = auditLogRepository.findByReservationId(reservation.reservationId());
    assertThat(logs).hasSize(2);
    assertThat(logs.get(1).action()).isEqualTo("REJECTED");
    assertThat(logs.get(1).reason()).isEqualTo("Not suitable");
  }

  @Test
  void cancel_writesCancelledAuditLog() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.createWithSlotValidation(
            slot.slotId(), "u-1", "idem-audit-4", "Taro", "t@e.com", 1);

    reservationService.cancel(reservation.reservationId(), "Changed mind");

    List<AuditLog> logs = auditLogRepository.findByReservationId(reservation.reservationId());
    assertThat(logs).hasSize(2);
    assertThat(logs.get(1).action()).isEqualTo("CANCELLED");
    assertThat(logs.get(1).reason()).isEqualTo("Changed mind");
  }

  @Test
  void waitlist_writesWaitlistedAuditLog() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.createWithSlotValidation(
            slot.slotId(), "u-1", "idem-audit-5", "Taro", "t@e.com", 1);

    reservationService.waitlist(reservation.reservationId());

    List<AuditLog> logs = auditLogRepository.findByReservationId(reservation.reservationId());
    assertThat(logs).hasSize(2);
    assertThat(logs.get(1).action()).isEqualTo("WAITLISTED");
  }

  @Test
  void autoPromotion_writesAuditLogForPromotedReservation() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 2);
    Reservation res1 =
        reservationRepository.createWithSlotValidation(
            slot.slotId(), "u-1", "idem-audit-6a", "A", "a@e.com", 2);
    Reservation res2 =
        reservationRepository.createWithSlotValidation(
            slot.slotId(), "u-2", "idem-audit-6b", "B", "b@e.com", 1);

    reservationService.approve(res1.reservationId());
    reservationService.waitlist(res2.reservationId());

    // Cancel approved reservation: should auto-promote waitlisted
    reservationService.cancel(res1.reservationId(), "Cancel first");

    // res2 should have CREATED + WAITLISTED + APPROVED (auto-promoted) audit entries
    List<AuditLog> res2Logs = auditLogRepository.findByReservationId(res2.reservationId());
    assertThat(res2Logs).hasSize(3);
    assertThat(res2Logs.get(2).action()).isEqualTo("APPROVED");
    assertThat(res2Logs.get(2).reason()).isEqualTo("Auto-promoted from waitlist");
  }
}
