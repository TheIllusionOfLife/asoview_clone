package com.asoviewclone.analytics.dashboard;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.analytics.dashboard.dto.RevenueSummaryResponse;
import com.asoviewclone.analytics.security.FirebaseTokenFilter;
import com.asoviewclone.analytics.security.SecurityConfig;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, FirebaseTokenFilter.class})
class DashboardControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DashboardService dashboardService;
  @MockitoBean private FirebaseAuth firebaseAuth;

  @Test
  void unauthenticated_returnsForbidden() throws Exception {
    mockMvc.perform(get("/v1/admin/analytics/revenue-summary")).andExpect(status().isForbidden());
  }

  @Test
  void authenticated_returns200() throws Exception {
    when(dashboardService.getRevenueSummary())
        .thenReturn(new RevenueSummaryResponse(0, 0, 0.0, null, null));
    mockMvc
        .perform(get("/v1/admin/analytics/revenue-summary").with(user("admin")))
        .andExpect(status().isOk());
  }
}
