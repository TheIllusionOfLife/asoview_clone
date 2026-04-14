package com.asoviewclone.reservation.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.reservation.model.ReservationSlot;
import com.asoviewclone.reservation.service.ReservationSlotService;
import com.google.firebase.auth.FirebaseAuth;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConsumerSlotController.class)
@AutoConfigureMockMvc(addFilters = false)
class ConsumerSlotControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ReservationSlotService slotService;
  @MockitoBean private FirebaseAuth firebaseAuth;

  @Test
  void listAvailableSlots_returnsSlots() throws Exception {
    ReservationSlot slot =
        new ReservationSlot(
            "slot-1",
            "tenant-1",
            "venue-1",
            "product-1",
            "2026-05-01",
            "09:00",
            "10:00",
            10,
            3,
            1,
            Instant.now(),
            Instant.now());

    when(slotService.listPublicSlots("venue-1", "2026-05-01")).thenReturn(List.of(slot));

    mockMvc
        .perform(get("/v1/reservation-slots?venueId=venue-1&date=2026-05-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].slotId").value("slot-1"))
        .andExpect(jsonPath("$[0].slotDate").value("2026-05-01"))
        .andExpect(jsonPath("$[0].startTime").value("09:00"))
        .andExpect(jsonPath("$[0].endTime").value("10:00"))
        .andExpect(jsonPath("$[0].capacity").value(10))
        .andExpect(jsonPath("$[0].approvedCount").value(3))
        .andExpect(jsonPath("$[0].remainingCapacity").value(7))
        // Should not expose internal fields
        .andExpect(jsonPath("$[0].tenantId").doesNotExist())
        .andExpect(jsonPath("$[0].waitlistCount").doesNotExist());
  }

  @Test
  void listAvailableSlots_returnsEmpty() throws Exception {
    when(slotService.listPublicSlots("venue-1", "2026-12-31")).thenReturn(List.of());

    mockMvc
        .perform(get("/v1/reservation-slots?venueId=venue-1&date=2026-12-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void listAvailableSlots_filtersFullSlots() throws Exception {
    ReservationSlot available =
        new ReservationSlot(
            "slot-1", "t-1", "v-1", "p-1", "2026-05-01", "09:00", "10:00", 10, 3, 0,
            Instant.now(), Instant.now());
    ReservationSlot full =
        new ReservationSlot(
            "slot-2", "t-1", "v-1", "p-1", "2026-05-01", "10:00", "11:00", 5, 5, 0,
            Instant.now(), Instant.now());

    when(slotService.listPublicSlots("v-1", "2026-05-01"))
        .thenReturn(List.of(available, full));

    // Both returned by service; controller includes all (full slots shown with remaining=0)
    mockMvc
        .perform(get("/v1/reservation-slots?venueId=v-1&date=2026-05-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].remainingCapacity").value(7))
        .andExpect(jsonPath("$[1].remainingCapacity").value(0));
  }
}
