package com.asoviewclone.reservation.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.reservation.model.SlotUtilization;
import com.asoviewclone.reservation.repository.ReservationRepository;
import com.asoviewclone.reservation.repository.ReservationSlotRepository;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(
    username = "admin-1",
    roles = {"USER", "ADMIN"})
class DashboardControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ReservationRepository reservationRepository;
  @MockitoBean private ReservationSlotRepository slotRepository;
  @MockitoBean private FirebaseAuth firebaseAuth;

  @Test
  void getDashboard_returnsSummary() throws Exception {
    when(reservationRepository.countByStatus("venue-1", null))
        .thenReturn(Map.of("PENDING_APPROVAL", 3L, "APPROVED", 5L));
    when(slotRepository.getUtilization("venue-1", null))
        .thenReturn(new SlotUtilization(10, 100, 50));

    mockMvc
        .perform(get("/v1/op/dashboard?venueId=venue-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reservationCounts.PENDING_APPROVAL").value(3))
        .andExpect(jsonPath("$.reservationCounts.APPROVED").value(5))
        .andExpect(jsonPath("$.slotUtilization.totalSlots").value(10))
        .andExpect(jsonPath("$.slotUtilization.totalCapacity").value(100))
        .andExpect(jsonPath("$.slotUtilization.totalApproved").value(50));
  }

  @Test
  void getMyVenues_returnsList() throws Exception {
    when(slotRepository.findDistinctVenueIds(null)).thenReturn(List.of("venue-1", "venue-2"));

    mockMvc
        .perform(get("/v1/op/me/venues"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0]").value("venue-1"))
        .andExpect(jsonPath("$[1]").value("venue-2"));
  }
}
