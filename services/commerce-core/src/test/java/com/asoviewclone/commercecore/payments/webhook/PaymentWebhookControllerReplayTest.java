package com.asoviewclone.commercecore.payments.webhook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.commercecore.identity.repository.TenantUserRepository;
import com.asoviewclone.commercecore.identity.repository.UserRepository;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.model.PaymentGatewayEvent;
import com.asoviewclone.commercecore.payments.service.PaymentGateway;
import com.asoviewclone.commercecore.payments.service.PaymentService;
import com.google.firebase.auth.FirebaseAuth;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pitfall 11 (PR #22, regression of PR #21): if anyone replaces {@code
 * processedEvents.insertIfMissing(...)} with {@code processedEvents.save(new
 * ProcessedWebhookEvent(...))}, the assigned-{@code @Id} entity routes through Hibernate's {@code
 * merge()} on the second delivery and Stripe's retried event re-confirms the payment a second time
 * — silent double-charge.
 *
 * <p>This test sends the same signed Stripe payload twice through the controller and asserts the
 * service-level confirm is invoked exactly once. It complements {@link
 * PaymentWebhookControllerTest#duplicateEventIsSkipped()} which exercises the single-delivery
 * branch via a pre-stubbed {@code insertIfMissing} return value; here we exercise the
 * sequential-delivery shape that the {@code save()} regression actually broke in production.
 */
@WebMvcTest(PaymentWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentWebhookControllerReplayTest {

  private static final String EVENT_ID = "evt_replay_1";
  private static final String PI_ID = "pi_replay_1";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PaymentGateway paymentGateway;
  @MockitoBean private PaymentService paymentService;
  @MockitoBean private ProcessedWebhookEventRepository processedEvents;
  @MockitoBean private FirebaseAuth firebaseAuth;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private TenantUserRepository tenantUserRepository;

  @Test
  void sequentialDuplicateDeliveryConfirmsExactlyOnce() throws Exception {
    when(paymentGateway.verifyWebhook(anyString(), any()))
        .thenReturn(
            new PaymentGatewayEvent(
                EVENT_ID, PI_ID, PaymentGatewayEvent.Status.SUCCEEDED, "STRIPE"));

    Payment payment = new Payment("order-replay-1", "user-1", new BigDecimal("100"), "JPY", "idem");
    payment.setProviderPaymentId(PI_ID);
    when(paymentService.confirmByProviderPaymentId(PI_ID)).thenReturn(payment);

    // First delivery: gate returns 1 (we won the race) → confirm runs.
    // Second delivery: gate returns 0 (replay row already present) → confirm skipped.
    when(processedEvents.insertIfMissing("STRIPE", EVENT_ID)).thenReturn(1).thenReturn(0);

    // First delivery
    mockMvc
        .perform(
            post("/v1/payments/webhooks/stripe")
                .header("Stripe-Signature", "t=0,v1=x")
                .content("{}"))
        .andExpect(status().isOk());

    // Second delivery (Stripe retries the same event)
    mockMvc
        .perform(
            post("/v1/payments/webhooks/stripe")
                .header("Stripe-Signature", "t=0,v1=x")
                .content("{}"))
        .andExpect(status().isOk());

    verify(processedEvents, times(2)).insertIfMissing("STRIPE", EVENT_ID);
    // The critical assertion: confirm runs ONCE across two deliveries.
    verify(paymentService, times(1)).confirmByProviderPaymentId(PI_ID);
  }
}
