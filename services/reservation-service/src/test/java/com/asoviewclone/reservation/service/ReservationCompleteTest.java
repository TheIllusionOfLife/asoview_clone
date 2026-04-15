package com.asoviewclone.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asoviewclone.reservation.exception.ConflictException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(SpannerEmulatorConfig.class)
@ActiveProfiles("test")
class ReservationCompleteTest {

  @Autowired private ReservationService reservationService;
  @Autowired private ReservationSlotRepository slotRepository;
  @Autowired private ReservationRepository reservationRepository;
  @MockitoBean private EmailService emailService;

  @BeforeEach
  void cleanup() {
    reservationRepository.deleteAll();
    slotRepository.deleteAll();
  }

  @Test
  void complete_approved_transitionsToCompleted() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.createWithSlotValidation(
            slot.slotId(), "u-1", "idem-complete-ok", "Taro", "t@e.com", 2);

    reservationService.approve(reservation.reservationId());

    Reservation completed = reservationService.complete(reservation.reservationId());

    assertThat(completed.status()).isEqualTo(ReservationStatus.COMPLETED);
  }

  @Test
  void complete_pendingApproval_fails() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.createWithSlotValidation(
            slot.slotId(), "u-1", "idem-complete-pa", "Taro", "t@e.com", 1);

    assertThatThrownBy(() -> reservationService.complete(reservation.reservationId()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void complete_alreadyCancelled_fails() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);
    Reservation reservation =
        reservationRepository.createWithSlotValidation(
            slot.slotId(), "u-1", "idem-complete-cancel", "Taro", "t@e.com", 1);

    reservationService.cancel(reservation.reservationId(), "Changed mind");

    assertThatThrownBy(() -> reservationService.complete(reservation.reservationId()))
        .isInstanceOf(ConflictException.class);
  }
}
