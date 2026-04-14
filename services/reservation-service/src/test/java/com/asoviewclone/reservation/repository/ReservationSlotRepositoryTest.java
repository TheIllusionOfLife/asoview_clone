package com.asoviewclone.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.reservation.model.ReservationSlot;
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
class ReservationSlotRepositoryTest {

  @Autowired private ReservationSlotRepository repository;

  @BeforeEach
  void cleanup() {
    repository.deleteAll();
  }

  @Test
  void createAndFindById() {
    ReservationSlot created =
        repository.create("tenant-1", "venue-1", "product-1", "2026-05-01", "09:00", "10:00", 10);

    assertThat(created.slotId()).isNotNull();
    assertThat(created.tenantId()).isEqualTo("tenant-1");
    assertThat(created.venueId()).isEqualTo("venue-1");
    assertThat(created.capacity()).isEqualTo(10);
    assertThat(created.approvedCount()).isZero();
    assertThat(created.waitlistCount()).isZero();

    ReservationSlot found = repository.findById(created.slotId()).orElseThrow();
    assertThat(found.slotId()).isEqualTo(created.slotId());
    assertThat(found.capacity()).isEqualTo(10);
  }

  @Test
  void findByVenueAndDate() {
    repository.create("tenant-1", "venue-1", "product-1", "2026-05-01", "09:00", "10:00", 10);
    repository.create("tenant-1", "venue-1", "product-1", "2026-05-01", "10:00", "11:00", 5);
    repository.create("tenant-1", "venue-1", "product-1", "2026-05-02", "09:00", "10:00", 8);
    repository.create("tenant-1", "venue-2", "product-2", "2026-05-01", "09:00", "10:00", 3);

    List<ReservationSlot> result = repository.findByVenueAndDate("venue-1", "2026-05-01");
    assertThat(result).hasSize(2);
    assertThat(result).allMatch(s -> s.venueId().equals("venue-1"));
    assertThat(result).allMatch(s -> s.slotDate().equals("2026-05-01"));
  }

  @Test
  void findByVenueAndDate_emptyWhenNoMatch() {
    repository.create("tenant-1", "venue-1", "product-1", "2026-05-01", "09:00", "10:00", 10);

    List<ReservationSlot> result = repository.findByVenueAndDate("venue-1", "2026-12-31");
    assertThat(result).isEmpty();
  }
}
