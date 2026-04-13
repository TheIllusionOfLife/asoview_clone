package com.asoviewclone.reservation.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.reservation.model.ReservationSlot;
import com.asoviewclone.reservation.service.ReservationSlotService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReservationSlotController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationSlotControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ReservationSlotService slotService;

  @Test
  void createSlot_returns201() throws Exception {
    ReservationSlot slot =
        new ReservationSlot(
            "slot-1", "tenant-1", "venue-1", "product-1", "2026-05-01", "09:00", "10:00", 10, 0,
            0, Instant.now(), Instant.now());

    when(slotService.createSlot(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyInt()))
        .thenReturn(slot);

    mockMvc
        .perform(
            post("/v1/op/reservation-slots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "venueId": "venue-1",
                      "productId": "product-1",
                      "slotDate": "2026-05-01",
                      "startTime": "09:00",
                      "endTime": "10:00",
                      "capacity": 10
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slotId").value("slot-1"))
        .andExpect(jsonPath("$.capacity").value(10));
  }

  @Test
  void listSlots_returnsSlots() throws Exception {
    ReservationSlot slot =
        new ReservationSlot(
            "slot-1", "tenant-1", "venue-1", "product-1", "2026-05-01", "09:00", "10:00", 10, 0,
            0, Instant.now(), Instant.now());

    when(slotService.listSlots("venue-1", "2026-05-01")).thenReturn(List.of(slot));

    mockMvc
        .perform(get("/v1/op/reservation-slots?venueId=venue-1&date=2026-05-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].slotId").value("slot-1"));
  }

  @Test
  void listSlots_returnsEmptyWhenNoMatch() throws Exception {
    when(slotService.listSlots("venue-1", "2026-12-31")).thenReturn(List.of());

    mockMvc
        .perform(get("/v1/op/reservation-slots?venueId=venue-1&date=2026-12-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }
}
