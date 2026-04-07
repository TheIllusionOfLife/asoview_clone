package com.asoviewclone.commercecore.wallet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.commercecore.identity.repository.TenantUserRepository;
import com.asoviewclone.commercecore.identity.repository.UserRepository;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import com.asoviewclone.commercecore.wallet.controller.WalletController;
import com.asoviewclone.commercecore.wallet.service.WalletService;
import com.asoviewclone.common.error.NotFoundException;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WalletController.class)
@AutoConfigureMockMvc(addFilters = false)
class WalletControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private WalletService walletService;
  @MockitoBean private FirebaseAuth firebaseAuth;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private TenantUserRepository tenantUserRepository;

  @AfterEach
  void clearAuth() {
    SecurityContextHolder.clearContext();
  }

  private static UUID setAuth() {
    UUID uid = UUID.randomUUID();
    AuthenticatedUser principal =
        new AuthenticatedUser("firebase-" + uid, "u@example.com", uid, Map.of());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
    return uid;
  }

  @Test
  void applePassReturnsBytesWhenOwned() throws Exception {
    UUID uid = setAuth();
    byte[] body = new byte[] {1, 2, 3, 4};
    when(walletService.buildApplePass(eq("tp-1"), eq(uid.toString()))).thenReturn(body);

    mockMvc
        .perform(get("/v1/me/tickets/tp-1/apple-pass"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/vnd.apple.pkpass"))
        .andExpect(content().bytes(body));
  }

  @Test
  void applePassReturns404WhenNotOwned() throws Exception {
    setAuth();
    Mockito.when(walletService.buildApplePass(anyString(), anyString()))
        .thenThrow(new NotFoundException("TicketPass", "tp-x"));

    mockMvc.perform(get("/v1/me/tickets/tp-x/apple-pass")).andExpect(status().isNotFound());
  }

  @Test
  void googleLinkReturnsSaveUrlWhenOwned() throws Exception {
    UUID uid = setAuth();
    when(walletService.buildGoogleSaveUrl(eq("tp-2"), eq(uid.toString())))
        .thenReturn("https://pay.google.com/gp/v/save/jwt-here");

    mockMvc
        .perform(get("/v1/me/tickets/tp-2/google-pass-link"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saveUrl").value("https://pay.google.com/gp/v/save/jwt-here"));
  }

  @Test
  void googleLinkReturns404WhenNotOwned() throws Exception {
    setAuth();
    Mockito.when(walletService.buildGoogleSaveUrl(any(), any()))
        .thenThrow(new NotFoundException("TicketPass", "tp-x"));

    mockMvc.perform(get("/v1/me/tickets/tp-x/google-pass-link")).andExpect(status().isNotFound());
  }
}
