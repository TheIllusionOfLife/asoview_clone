package com.asoviewclone.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.model.ReservationSlot;
import com.asoviewclone.reservation.model.ReservationStatus;
import com.asoviewclone.reservation.repository.ReservationRepository;
import com.asoviewclone.reservation.repository.ReservationSlotRepository;
import com.asoviewclone.reservation.testutil.SpannerEmulatorConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(SpannerEmulatorConfig.class)
@ActiveProfiles("test")
class ReservationApprovalTest {

  @Autowired private ReservationService reservationService;
  @Autowired private ReservationSlotRepository slotRepository;
  @Autowired private ReservationRepository reservationRepository;

  @BeforeEach
  void cleanup() {
    reservationRepository.deleteAll();
    slotRepository.deleteAll();
  }

  @Test
  void approve_transitionsStatusAndIncrementsCapacity() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.create(
            "t-1", "v-1", slot.slotId(), "u-1", "idem-approve", "Taro", "t@e.com", 2);

    Reservation approved = reservationService.approve(reservation.reservationId());

    assertThat(approved.status()).isEqualTo(ReservationStatus.APPROVED);

    ReservationSlot updatedSlot = slotRepository.findById(slot.slotId()).orElseThrow();
    assertThat(updatedSlot.approvedCount()).isEqualTo(2);
  }

  @Test
  void approve_failsWhenCapacityExceeded() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 1);
    Reservation reservation =
        reservationRepository.create(
            "t-1", "v-1", slot.slotId(), "u-1", "idem-overcap", "Taro", "t@e.com", 2);

    assertThatThrownBy(() -> reservationService.approve(reservation.reservationId()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("capacity");

    Reservation unchanged =
        reservationRepository.findById(reservation.reservationId()).orElseThrow();
    assertThat(unchanged.status()).isEqualTo(ReservationStatus.PENDING_APPROVAL);
  }

  @Test
  void approve_failsWhenNotPendingApproval() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.create(
            "t-1", "v-1", slot.slotId(), "u-1", "idem-rejected", "Taro", "t@e.com", 1);

    reservationService.reject(reservation.reservationId(), "Not suitable");

    assertThatThrownBy(() -> reservationService.approve(reservation.reservationId()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reject_transitionsStatus() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.create(
            "t-1", "v-1", slot.slotId(), "u-1", "idem-reject", "Taro", "t@e.com", 1);

    Reservation rejected =
        reservationService.reject(reservation.reservationId(), "Schedule conflict");

    assertThat(rejected.status()).isEqualTo(ReservationStatus.REJECTED);
    assertThat(rejected.rejectReason()).isEqualTo("Schedule conflict");

    ReservationSlot unchangedSlot = slotRepository.findById(slot.slotId()).orElseThrow();
    assertThat(unchangedSlot.approvedCount()).isZero();
  }

  @Test
  void reject_failsWhenAlreadyTerminal() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.create(
            "t-1", "v-1", slot.slotId(), "u-1", "idem-terminal", "Taro", "t@e.com", 1);

    reservationService.reject(reservation.reservationId(), "No");

    assertThatThrownBy(
            () -> reservationService.reject(reservation.reservationId(), "Double reject"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void waitlist_transitionsStatus() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.create(
            "t-1", "v-1", slot.slotId(), "u-1", "idem-wl", "Taro", "t@e.com", 1);

    Reservation waitlisted = reservationService.waitlist(reservation.reservationId());

    assertThat(waitlisted.status()).isEqualTo(ReservationStatus.WAITLISTED);

    ReservationSlot updatedSlot = slotRepository.findById(slot.slotId()).orElseThrow();
    assertThat(updatedSlot.waitlistCount()).isEqualTo(1);
  }

  @Test
  void approve_waitlistedReservation_succeeds() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.create(
            "t-1", "v-1", slot.slotId(), "u-1", "idem-wl-approve", "Taro", "t@e.com", 1);

    reservationService.waitlist(reservation.reservationId());
    Reservation approved = reservationService.approve(reservation.reservationId());

    assertThat(approved.status()).isEqualTo(ReservationStatus.APPROVED);

    ReservationSlot updatedSlot = slotRepository.findById(slot.slotId()).orElseThrow();
    assertThat(updatedSlot.approvedCount()).isEqualTo(1);
    assertThat(updatedSlot.waitlistCount()).isZero();
  }
}
