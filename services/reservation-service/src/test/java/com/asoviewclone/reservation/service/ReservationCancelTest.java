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
class ReservationCancelTest {

  @Autowired private ReservationService reservationService;
  @Autowired private ReservationSlotRepository slotRepository;
  @Autowired private ReservationRepository reservationRepository;

  @BeforeEach
  void cleanup() {
    reservationRepository.deleteAll();
    slotRepository.deleteAll();
  }

  @Test
  void cancel_pendingApproval_succeeds() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.create(
            "t-1", "v-1", slot.slotId(), "u-1", "idem-cancel-pa", "Taro", "t@e.com", 2);

    Reservation cancelled =
        reservationService.cancel(reservation.reservationId(), "Changed my mind");

    assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
    assertThat(cancelled.cancelReason()).isEqualTo("Changed my mind");

    ReservationSlot unchangedSlot = slotRepository.findById(slot.slotId()).orElseThrow();
    assertThat(unchangedSlot.approvedCount()).isZero();
  }

  @Test
  void cancel_approved_releasesCapacity() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.create(
            "t-1", "v-1", slot.slotId(), "u-1", "idem-cancel-app", "Taro", "t@e.com", 3);

    reservationService.approve(reservation.reservationId());

    ReservationSlot afterApprove = slotRepository.findById(slot.slotId()).orElseThrow();
    assertThat(afterApprove.approvedCount()).isEqualTo(3);

    reservationService.cancel(reservation.reservationId(), "Operator cancelled");

    ReservationSlot afterCancel = slotRepository.findById(slot.slotId()).orElseThrow();
    assertThat(afterCancel.approvedCount()).isZero();
  }

  @Test
  void cancel_waitlisted_releasesWaitlistCount() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.create(
            "t-1", "v-1", slot.slotId(), "u-1", "idem-cancel-wl", "Taro", "t@e.com", 1);

    reservationService.waitlist(reservation.reservationId());

    ReservationSlot afterWaitlist = slotRepository.findById(slot.slotId()).orElseThrow();
    assertThat(afterWaitlist.waitlistCount()).isEqualTo(1);

    reservationService.cancel(reservation.reservationId(), "No longer interested");

    ReservationSlot afterCancel = slotRepository.findById(slot.slotId()).orElseThrow();
    assertThat(afterCancel.waitlistCount()).isZero();
  }

  @Test
  void cancel_terminal_fails() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.create(
            "t-1", "v-1", slot.slotId(), "u-1", "idem-cancel-term", "Taro", "t@e.com", 1);

    reservationService.reject(reservation.reservationId(), "No");

    assertThatThrownBy(
            () -> reservationService.cancel(reservation.reservationId(), "Try to cancel"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void waitlistPromotion_cancelApproved_thenApproveWaitlisted() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 2);

    Reservation res1 =
        reservationRepository.create(
            "t-1", "v-1", slot.slotId(), "u-1", "idem-promo-1", "A", "a@e.com", 2);
    Reservation res2 =
        reservationRepository.create(
            "t-1", "v-1", slot.slotId(), "u-2", "idem-promo-2", "B", "b@e.com", 1);

    reservationService.approve(res1.reservationId());
    reservationService.waitlist(res2.reservationId());

    ReservationSlot full = slotRepository.findById(slot.slotId()).orElseThrow();
    assertThat(full.approvedCount()).isEqualTo(2);
    assertThat(full.waitlistCount()).isEqualTo(1);

    reservationService.cancel(res1.reservationId(), "Cancel first");

    ReservationSlot freed = slotRepository.findById(slot.slotId()).orElseThrow();
    assertThat(freed.approvedCount()).isZero();

    Reservation promoted = reservationService.approve(res2.reservationId());
    assertThat(promoted.status()).isEqualTo(ReservationStatus.APPROVED);

    ReservationSlot afterPromo = slotRepository.findById(slot.slotId()).orElseThrow();
    assertThat(afterPromo.approvedCount()).isEqualTo(1);
    assertThat(afterPromo.waitlistCount()).isZero();
  }
}
