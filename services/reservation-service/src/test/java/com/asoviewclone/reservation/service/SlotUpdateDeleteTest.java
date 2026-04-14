package com.asoviewclone.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asoviewclone.reservation.exception.ConflictException;
import com.asoviewclone.reservation.exception.NotFoundException;
import com.asoviewclone.reservation.model.ReservationSlot;
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
class SlotUpdateDeleteTest {

  @Autowired private ReservationSlotService slotService;
  @Autowired private ReservationSlotRepository slotRepository;
  @Autowired private ReservationRepository reservationRepository;
  @Autowired private ReservationService reservationService;

  @BeforeEach
  void cleanup() {
    reservationRepository.deleteAll();
    slotRepository.deleteAll();
  }

  @Test
  void updateSlot_succeeds_whenNoActiveReservations() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);

    ReservationSlot updated = slotService.updateSlot(slot.slotId(), "09:30", "11:00", 20);

    assertThat(updated.startTime()).isEqualTo("09:30");
    assertThat(updated.endTime()).isEqualTo("11:00");
    assertThat(updated.capacity()).isEqualTo(20);
  }

  @Test
  void updateSlot_failsWhenNonTerminalReservationExists() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);
    reservationRepository.createWithSlotValidation(
        slot.slotId(), "u-1", "idem-upd-1", "Taro", "t@e.com", 2);

    assertThatThrownBy(() -> slotService.updateSlot(slot.slotId(), "09:00", "10:00", 20))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("active reservations");
  }

  @Test
  void updateSlot_succeedsWhenOnlyTerminalReservations() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);
    var reservation =
        reservationRepository.createWithSlotValidation(
            slot.slotId(), "u-1", "idem-upd-2", "Taro", "t@e.com", 2);
    reservationService.reject(reservation.reservationId(), "No");

    ReservationSlot updated = slotService.updateSlot(slot.slotId(), "09:00", "10:00", 5);
    assertThat(updated.capacity()).isEqualTo(5);
  }

  @Test
  void updateSlot_failsWhenNotFound() {
    assertThatThrownBy(() -> slotService.updateSlot("nonexistent", "09:00", "10:00", 10))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void deleteSlot_succeeds_whenNoActiveReservations() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);

    slotService.deleteSlot(slot.slotId());

    assertThat(slotRepository.findById(slot.slotId())).isEmpty();
  }

  @Test
  void deleteSlot_failsWhenPendingReservationExists() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);
    reservationRepository.createWithSlotValidation(
        slot.slotId(), "u-1", "idem-del-1", "Taro", "t@e.com", 1);

    assertThatThrownBy(() -> slotService.deleteSlot(slot.slotId()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("active reservations");
  }

  @Test
  void deleteSlot_failsWhenApprovedReservationExists() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);
    var reservation =
        reservationRepository.createWithSlotValidation(
            slot.slotId(), "u-1", "idem-del-2", "Taro", "t@e.com", 2);
    reservationService.approve(reservation.reservationId());

    assertThatThrownBy(() -> slotService.deleteSlot(slot.slotId()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void deleteSlot_failsWhenNotFound() {
    assertThatThrownBy(() -> slotService.deleteSlot("nonexistent"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void deleteSlot_succeedsWhenOnlyTerminalReservations() {
    ReservationSlot slot =
        slotRepository.create("t-1", "v-1", "p-1", "2026-06-01", "09:00", "10:00", 10);
    var reservation =
        reservationRepository.createWithSlotValidation(
            slot.slotId(), "u-1", "idem-del-3", "Taro", "t@e.com", 1);
    reservationService.cancel(reservation.reservationId(), "Changed mind");

    slotService.deleteSlot(slot.slotId());
    assertThat(slotRepository.findById(slot.slotId())).isEmpty();
  }
}
