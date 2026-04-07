package com.asoviewclone.commercecore.entitlements.controller;

import com.asoviewclone.commercecore.entitlements.model.TicketPassView;
import com.asoviewclone.commercecore.entitlements.service.EntitlementServiceImpl;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner-checked sibling of {@code GET /v1/me/tickets?orderId=}. The {@code /me/tickets} endpoint is
 * a user-scoped filter, so a cross-user request returns {@code 200 []} rather than {@code 404}.
 * That ambiguity does not match the consumer web app's {@code /tickets/[orderId]} page, which needs
 * a clean 404 contract identical to {@code GET /v1/orders/{id}}. This controller routes through the
 * order's owner check before fetching ticket passes, so cross-user accesses 404 and never leak
 * existence.
 */
@RestController
public class OrderTicketsController {

  private final EntitlementServiceImpl entitlementService;

  public OrderTicketsController(EntitlementServiceImpl entitlementService) {
    this.entitlementService = entitlementService;
  }

  @GetMapping("/v1/orders/{orderId}/tickets")
  public List<Map<String, Object>> listTicketsForOrder(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String orderId) {
    List<TicketPassView> views =
        entitlementService.listTicketPassViewsForOrderOwnedBy(orderId, user.userId().toString());
    return views.stream().map(OrderTicketsController::toResponse).toList();
  }

  private static Map<String, Object> toResponse(TicketPassView v) {
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
