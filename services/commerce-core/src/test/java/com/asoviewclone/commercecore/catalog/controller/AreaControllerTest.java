package com.asoviewclone.commercecore.catalog.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.asoviewclone.commercecore.identity.model.Venue;
import com.asoviewclone.commercecore.identity.repository.TenantUserRepository;
import com.asoviewclone.commercecore.identity.repository.UserRepository;
import com.asoviewclone.commercecore.identity.repository.VenueRepository;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AreaController.class)
@AutoConfigureMockMvc(addFilters = false)
class AreaControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private VenueRepository venueRepository;
  @MockitoBean private FirebaseAuth firebaseAuth;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private TenantUserRepository tenantUserRepository;

  @Test
  void listAreas_returnsSlugDerivedFromName() throws Exception {
    var tokyo = new Venue(UUID.randomUUID(), "Tokyo", "Tokyo, Japan", 35.6762, 139.6503);
    var yokohama = new Venue(UUID.randomUUID(), "Yokohama", "Yokohama, Japan", 35.4437, 139.6380);
    when(venueRepository.findAll()).thenReturn(List.of(tokyo, yokohama));

    mockMvc
        .perform(get("/v1/areas"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].name", is("Tokyo")))
        .andExpect(jsonPath("$[0].slug", is("tokyo")))
        .andExpect(jsonPath("$[1].name", is("Yokohama")))
        .andExpect(jsonPath("$[1].slug", is("yokohama")));
  }

  @Test
  void slug_lowercasesAndReplacesSpaces() throws Exception {
    var venue = new Venue(UUID.randomUUID(), "New York City", "NYC, USA", 40.7, -74.0);
    when(venueRepository.findAll()).thenReturn(List.of(venue));

    mockMvc
        .perform(get("/v1/areas"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].slug", is("new-york-city")));
  }

  @Test
  void slug_stripsPunctuationAndTrimsWhitespace() throws Exception {
    var venue = new Venue(UUID.randomUUID(), "  New/York (City)  ", "NYC, USA", 40.7, -74.0);
    when(venueRepository.findAll()).thenReturn(List.of(venue));

    mockMvc
        .perform(get("/v1/areas"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].slug", is("new-york-city")));
  }
}
