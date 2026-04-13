package com.asoviewclone.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.model.ReservationSlot;
import com.asoviewclone.reservation.model.ReservationStatus;
import com.asoviewclone.reservation.testutil.SpannerEmulatorConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(SpannerEmulatorConfig.class)
@ActiveProfiles("test")
class ReservationRepositoryTest {

  @Autowired private ReservationRepository repository;
  @Autowired private ReservationSlotRepository slotRepository;

  private ReservationSlot slot1;
  private ReservationSlot slot2;
  private ReservationSlot slot3;

  @BeforeEach
  void cleanup() {
    repository.deleteAll();
    slotRepository.deleteAll();
    slot1 = slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 10);
    slot2 = slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "10:00", "11:00", 10);
    slot3 = slotRepository.create("t-1", "v-1", "p-1", "2026-05-01", "11:00", "12:00", 10);
  }

  @Test
  void createAndFindById() {
    Reservation created =
        repository.createWithSlotValidation(
            slot1.slotId(), "user-1", "idem-1", "Taro Yamada", "taro@example.com", 2);

    assertThat(created.reservationId()).isNotNull();
    assertThat(created.status()).isEqualTo(ReservationStatus.PENDING_APPROVAL);
    assertThat(created.guestName()).isEqualTo("Taro Yamada");
    assertThat(created.guestCount()).isEqualTo(2);
    assertThat(created.tenantId()).isEqualTo("t-1");
    assertThat(created.venueId()).isEqualTo("v-1");
    assertThat(created.createdAt()).isNotNull();
    assertThat(created.updatedAt()).isNotNull();

    Optional<Reservation> found = repository.findById(created.reservationId());
    assertThat(found).isPresent();
    assertThat(found.get().status()).isEqualTo(ReservationStatus.PENDING_APPROVAL);
  }

  @Test
  void idempotencyKey_duplicateReturnsExisting() {
    Reservation first =
        repository.createWithSlotValidation(
            slot1.slotId(), "user-1", "idem-dup", "Taro", "t@e.com", 1);

    Optional<Reservation> existing = repository.findByIdempotencyKey("idem-dup");
    assertThat(existing).isPresent();
    assertThat(existing.get().reservationId()).isEqualTo(first.reservationId());
  }

  @Test
  void findByConsumerUserId() {
    repository.createWithSlotValidation(slot1.slotId(), "user-A", "idem-a", "A", "a@e.com", 1);
    repository.createWithSlotValidation(slot2.slotId(), "user-A", "idem-b", "A", "a@e.com", 2);
    repository.createWithSlotValidation(slot3.slotId(), "user-B", "idem-c", "B", "b@e.com", 1);

    List<Reservation> userAReservations = repository.findByConsumerUserId("user-A");
    assertThat(userAReservations).hasSize(2);
    assertThat(userAReservations).allMatch(r -> r.consumerUserId().equals("user-A"));
  }

  @Test
  void findByVenueAndStatus() {
    repository.createWithSlotValidation(slot1.slotId(), "u-1", "idem-1", "A", "a@e.com", 1);
    repository.createWithSlotValidation(slot2.slotId(), "u-2", "idem-2", "B", "b@e.com", 1);

    List<Reservation> pending =
        repository.findByVenueAndStatus("v-1", ReservationStatus.PENDING_APPROVAL);
    assertThat(pending).hasSize(2);
  }
}
