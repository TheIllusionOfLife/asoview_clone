package com.asoviewclone.reservation.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.model.ReservationStatus;
import com.asoviewclone.reservation.service.ReservationService;
import com.asoviewclone.reservation.service.ReservationService.CreateResult;
import com.google.firebase.auth.FirebaseAuth;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ReservationService reservationService;
  @MockitoBean private FirebaseAuth firebaseAuth;

  private static final Reservation SAMPLE =
      new Reservation(
          "res-1",
          "tenant-1",
          "venue-1",
          "slot-1",
          "user-1",
          ReservationStatus.PENDING_APPROVAL,
          "idem-1",
          "Taro Yamada",
          "taro@example.com",
          2,
          null,
          null,
          Instant.now(),
          Instant.now());

  @Test
  @WithMockUser(username = "user-1")
  void requestReservation_returns201() throws Exception {
    when(reservationService.requestReservation(
            anyString(), anyString(), anyString(), anyString(), eq("user-1"), anyInt()))
        .thenReturn(new CreateResult(SAMPLE, true));

    mockMvc
        .perform(
            post("/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "slotId": "slot-1",
                      "idempotencyKey": "idem-1",
                      "guestName": "Taro Yamada",
                      "guestEmail": "taro@example.com",
                      "guestCount": 2
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reservationId").value("res-1"))
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
  }

  @Test
  @WithMockUser(username = "user-1")
  void getReservation_returns200() throws Exception {
    when(reservationService.findById("res-1")).thenReturn(Optional.of(SAMPLE));

    mockMvc
        .perform(get("/v1/reservations/res-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reservationId").value("res-1"));
  }

  @Test
  @WithMockUser(username = "other-user")
  void getReservation_returns404WhenNotOwner() throws Exception {
    when(reservationService.findById("res-1")).thenReturn(Optional.of(SAMPLE));

    mockMvc.perform(get("/v1/reservations/res-1")).andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(username = "user-1")
  void getReservation_returns404WhenNotFound() throws Exception {
    when(reservationService.findById("nonexistent")).thenReturn(Optional.empty());

    mockMvc.perform(get("/v1/reservations/nonexistent")).andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(username = "user-1")
  void listMyReservations_returnsList() throws Exception {
    when(reservationService.findByConsumerUserId("user-1")).thenReturn(List.of(SAMPLE));

    mockMvc
        .perform(get("/v1/me/reservations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  @WithMockUser(username = "user-1")
  void cancelReservation_returns200() throws Exception {
    when(reservationService.findById("res-1")).thenReturn(Optional.of(SAMPLE));
    Reservation cancelled =
        new Reservation(
            "res-1",
            "tenant-1",
            "venue-1",
            "slot-1",
            "user-1",
            ReservationStatus.CANCELLED,
            "idem-1",
            "Taro Yamada",
            "taro@example.com",
            2,
            null,
            "Changed mind",
            Instant.now(),
            Instant.now());
    when(reservationService.cancel("res-1", "Changed mind")).thenReturn(cancelled);

    mockMvc
        .perform(
            put("/v1/reservations/res-1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reason": "Changed mind"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  @WithMockUser(username = "other-user")
  void cancelReservation_returns404WhenNotOwner() throws Exception {
    when(reservationService.findById("res-1")).thenReturn(Optional.of(SAMPLE));

    mockMvc
        .perform(
            put("/v1/reservations/res-1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reason": "Hacking"}
                    """))
        .andExpect(status().isNotFound());
  }
}
