package com.asoviewclone.reservation.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.model.ReservationStatus;
import com.asoviewclone.reservation.service.ReservationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReservationOperatorController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationOperatorControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ReservationService reservationService;

  private static final Reservation SAMPLE =
      new Reservation(
          "res-1", "tenant-1", "venue-1", "slot-1", "user-1",
          ReservationStatus.PENDING_APPROVAL, "idem-1", "Taro", "t@e.com", 2,
          null, null, Instant.now(), Instant.now());

  @Test
  void listReservations_returnsFilteredList() throws Exception {
    when(reservationService.findByVenueAndStatus("venue-1", ReservationStatus.PENDING_APPROVAL))
        .thenReturn(List.of(SAMPLE));

    mockMvc
        .perform(
            get("/v1/op/reservations?venueId=venue-1&status=PENDING_APPROVAL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].reservationId").value("res-1"));
  }

  @Test
  void getReservation_returns200() throws Exception {
    when(reservationService.findById("res-1")).thenReturn(Optional.of(SAMPLE));

    mockMvc
        .perform(get("/v1/op/reservations/res-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reservationId").value("res-1"));
  }

  @Test
  void approve_returns200() throws Exception {
    Reservation approved =
        new Reservation(
            "res-1", "tenant-1", "venue-1", "slot-1", "user-1",
            ReservationStatus.APPROVED, "idem-1", "Taro", "t@e.com", 2,
            null, null, Instant.now(), Instant.now());
    when(reservationService.approve("res-1")).thenReturn(approved);

    mockMvc
        .perform(put("/v1/op/reservations/res-1/approve"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void reject_returns200() throws Exception {
    Reservation rejected =
        new Reservation(
            "res-1", "tenant-1", "venue-1", "slot-1", "user-1",
            ReservationStatus.REJECTED, "idem-1", "Taro", "t@e.com", 2,
            "Not suitable", null, Instant.now(), Instant.now());
    when(reservationService.reject("res-1", "Not suitable")).thenReturn(rejected);

    mockMvc
        .perform(
            put("/v1/op/reservations/res-1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason": "Not suitable"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectReason").value("Not suitable"));
  }

  @Test
  void cancel_returns200() throws Exception {
    Reservation cancelled =
        new Reservation(
            "res-1", "tenant-1", "venue-1", "slot-1", "user-1",
            ReservationStatus.CANCELLED, "idem-1", "Taro", "t@e.com", 2,
            null, "Operator decision", Instant.now(), Instant.now());
    when(reservationService.cancel("res-1", "Operator decision")).thenReturn(cancelled);

    mockMvc
        .perform(
            put("/v1/reservations/res-1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason": "Operator decision"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }
}
