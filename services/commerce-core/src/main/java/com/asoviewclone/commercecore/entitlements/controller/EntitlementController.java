package com.asoviewclone.commercecore.entitlements.controller;

import com.asoviewclone.commercecore.entitlements.model.TicketPassView;
import com.asoviewclone.commercecore.entitlements.service.EntitlementServiceImpl;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/tickets")
public class EntitlementController {

  private final EntitlementServiceImpl entitlementService;

  public EntitlementController(EntitlementServiceImpl entitlementService) {
    this.entitlementService = entitlementService;
  }

  @GetMapping
  public List<Map<String, Object>> listMyTickets(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(value = "orderId", required = false) String orderId) {
    // The view includes orderId + validFrom/validUntil from the joined entitlement so the
    // /tickets/[orderId] page can gate QR display on the validity window without a second call.
    List<TicketPassView> views =
        entitlementService.listUserTicketPassViews(user.userId().toString(), orderId);
    return views.stream().map(EntitlementController::toResponse).toList();
  }

  private static Map<String, Object> toResponse(TicketPassView v) {
    // HashMap (not Map.of) so we can carry nullable validity timestamps without an NPE.
    Map<String, Object> out = new HashMap<>();
    out.put("ticketPassId", v.ticketPassId());
    out.put("entitlementId", v.entitlementId());
    out.put("orderId", v.orderId());
    out.put("qrCodePayload", v.qrCodePayload());
    out.put("status", v.status().name());
    out.put("validFrom", v.validFrom() != null ? v.validFrom().toString() : null);
    out.put("validUntil", v.validUntil() != null ? v.validUntil().toString() : null);
    return out;
  }
}
