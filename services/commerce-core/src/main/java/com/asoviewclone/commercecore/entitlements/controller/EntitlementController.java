package com.asoviewclone.commercecore.entitlements.controller;

import com.asoviewclone.commercecore.entitlements.model.TicketPass;
import com.asoviewclone.commercecore.entitlements.service.EntitlementServiceImpl;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/tickets")
public class EntitlementController {

  private final EntitlementServiceImpl entitlementService;

  public EntitlementController(EntitlementServiceImpl entitlementService) {
    this.entitlementService = entitlementService;
  }

  @GetMapping
  public List<Map<String, Object>> listMyTickets(@AuthenticationPrincipal AuthenticatedUser user) {
    List<TicketPass> passes = entitlementService.listUserTicketPasses(user.userId().toString());
    return passes.stream()
        .map(
            p ->
                Map.<String, Object>of(
                    "ticketPassId", p.ticketPassId(),
                    "entitlementId", p.entitlementId(),
                    "qrCodePayload", p.qrCodePayload(),
                    "status", p.status().name()))
        .toList();
  }
}
