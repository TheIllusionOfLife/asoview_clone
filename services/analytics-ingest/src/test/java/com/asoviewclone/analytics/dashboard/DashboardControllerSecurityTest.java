package com.asoviewclone.analytics.dashboard;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.analytics.dashboard.dto.RevenueSummaryResponse;
import com.asoviewclone.analytics.security.FirebaseTokenFilter;
import com.asoviewclone.analytics.security.SecurityConfig;
import com.asoviewclone.common.error.JsonAccessDeniedHandler;
import com.asoviewclone.common.error.JsonAuthenticationEntryPoint;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// JsonAccessDeniedHandler + JsonAuthenticationEntryPoint are
// constructor-injected by SecurityConfig, so the slice needs them in the
// context. @WebMvcTest's default scan doesn't cover com.asoviewclone.common.
@WebMvcTest(DashboardController.class)
@Import({
  SecurityConfig.class,
  FirebaseTokenFilter.class,
  JsonAccessDeniedHandler.class,
  JsonAuthenticationEntryPoint.class
})
class DashboardControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DashboardService dashboardService;
  @MockitoBean private FirebaseAuth firebaseAuth;

  @Test
  void noToken_returnsUnauthorized() throws Exception {
    // Was isForbidden() before JsonAuthenticationEntryPoint landed. The
    // REST-canonical split is 401 for "no authentication", 403 for
    // "authenticated but not authorized". See SecurityConfig rationale.
    mockMvc
        .perform(get("/v1/admin/analytics/revenue-summary"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void invalidToken_returns401() throws Exception {
    when(firebaseAuth.verifyIdToken("bad-token")).thenThrow(new RuntimeException("invalid"));
    mockMvc
        .perform(
            get("/v1/admin/analytics/revenue-summary").header("Authorization", "Bearer bad-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void validToken_withoutAdminClaim_returnsForbidden() throws Exception {
    FirebaseToken token = org.mockito.Mockito.mock(FirebaseToken.class);
    when(token.getUid()).thenReturn("user-1");
    when(token.getClaims()).thenReturn(Map.of());
    when(firebaseAuth.verifyIdToken("user-token")).thenReturn(token);

    mockMvc
        .perform(
            get("/v1/admin/analytics/revenue-summary").header("Authorization", "Bearer user-token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void validToken_withAdminClaim_returns200() throws Exception {
    FirebaseToken token = org.mockito.Mockito.mock(FirebaseToken.class);
    when(token.getUid()).thenReturn("admin-1");
    when(token.getClaims()).thenReturn(Map.of("admin", true));
    when(firebaseAuth.verifyIdToken("admin-token")).thenReturn(token);
    when(dashboardService.getRevenueSummary())
        .thenReturn(new RevenueSummaryResponse(0, 0, 0.0, null, null));

    mockMvc
        .perform(
            get("/v1/admin/analytics/revenue-summary")
                .header("Authorization", "Bearer admin-token"))
        .andExpect(status().isOk());
  }
}
