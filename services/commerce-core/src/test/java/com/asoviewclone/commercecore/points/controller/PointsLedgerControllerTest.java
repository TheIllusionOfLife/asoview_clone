package com.asoviewclone.commercecore.points.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.commercecore.identity.repository.TenantUserRepository;
import com.asoviewclone.commercecore.identity.repository.UserRepository;
import com.asoviewclone.commercecore.points.model.PointLedgerEntry;
import com.asoviewclone.commercecore.points.model.PointReason;
import com.asoviewclone.commercecore.points.service.PointService;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PointController.class)
@AutoConfigureMockMvc(addFilters = false)
class PointsLedgerControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PointService pointService;
  @MockitoBean private FirebaseAuth firebaseAuth;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private TenantUserRepository tenantUserRepository;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private UUID setAuth() {
    UUID uid = UUID.randomUUID();
    AuthenticatedUser principal =
        new AuthenticatedUser("firebase-" + uid, "test@example.com", uid, Map.of());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
    return uid;
  }

  @Test
  void getLedger_returnsPaginatedEntries() throws Exception {
    UUID userId = setAuth();

    var entry1 = new PointLedgerEntry(userId, 100L, PointReason.EARN_PURCHASE, "order-1");
    var entry2 = new PointLedgerEntry(userId, -50L, PointReason.BURN_PURCHASE, "order-2");
    Page<PointLedgerEntry> page = new PageImpl<>(List.of(entry1, entry2));

    when(pointService.getLedger(any(), any())).thenReturn(page);

    mockMvc
        .perform(get("/v1/me/points/ledger"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[0].direction", is("EARN")))
        .andExpect(jsonPath("$.content[0].amount", is(100)))
        .andExpect(jsonPath("$.content[0].reason", is("EARN_PURCHASE")))
        .andExpect(jsonPath("$.content[0].referenceId", is("order-1")))
        .andExpect(jsonPath("$.content[1].direction", is("BURN")))
        .andExpect(jsonPath("$.content[1].amount", is(50)));
  }

  @Test
  void getLedger_emptyPage() throws Exception {
    setAuth();

    when(pointService.getLedger(any(), any())).thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/v1/me/points/ledger"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)));
  }
}
