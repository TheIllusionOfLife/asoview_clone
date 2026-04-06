package com.asoviewclone.commercecore.payments.webhook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
import com.asoviewclone.common.error.ValidationException;
import com.google.firebase.auth.FirebaseAuth;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-slice tests for the Stripe webhook path. Covers: bad signature, ignored event, happy
 * path, replay (duplicate event id), and unknown provider payment id. Stripe-java itself is not
 * invoked — the gateway is mocked at the {@link PaymentGateway#verifyWebhook} boundary.
 */
@WebMvcTest(PaymentWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentWebhookControllerTest {

  private static final String EVENT_ID = "evt_test_1";
  private static final String PI_ID = "pi_test_1";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PaymentGateway paymentGateway;
  @MockitoBean private PaymentService paymentService;
  @MockitoBean private ProcessedWebhookEventRepository processedEvents;
  @MockitoBean private FirebaseAuth firebaseAuth;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private TenantUserRepository tenantUserRepository;

  @Test
  void invalidSignatureReturns400() throws Exception {
    when(paymentGateway.verifyWebhook(anyString(), any()))
        .thenThrow(new ValidationException("bad sig"));

    mockMvc
        .perform(
            post("/v1/payments/webhooks/stripe")
                .header("Stripe-Signature", "t=0,v1=bad")
                .content("{}"))
        .andExpect(status().isBadRequest());
    verify(processedEvents, never()).save(any());
  }

  @Test
  void ignoredEventReturnsOkAndNoAction() throws Exception {
    when(paymentGateway.verifyWebhook(anyString(), any()))
        .thenReturn(
            new PaymentGatewayEvent(EVENT_ID, null, PaymentGatewayEvent.Status.IGNORED, "STRIPE"));

    mockMvc
        .perform(
            post("/v1/payments/webhooks/stripe")
                .header("Stripe-Signature", "t=0,v1=x")
                .content("{}"))
        .andExpect(status().isOk());
    verify(processedEvents, never()).save(any());
    verify(paymentService, never()).confirmByProviderPaymentId(anyString());
  }

  @Test
  void successDelegatesToConfirmByProviderPaymentId() throws Exception {
    when(paymentGateway.verifyWebhook(anyString(), any()))
        .thenReturn(
            new PaymentGatewayEvent(
                EVENT_ID, PI_ID, PaymentGatewayEvent.Status.SUCCEEDED, "STRIPE"));
    Payment payment = new Payment("order-1", "user-1", new BigDecimal("100"), "JPY", "idem-1");
    payment.setProviderPaymentId(PI_ID);
    when(paymentService.confirmByProviderPaymentId(PI_ID)).thenReturn(payment);

    mockMvc
        .perform(
            post("/v1/payments/webhooks/stripe")
                .header("Stripe-Signature", "t=0,v1=x")
                .content("{}"))
        .andExpect(status().isOk());
    verify(processedEvents, times(1)).save(any());
    verify(paymentService, times(1)).confirmByProviderPaymentId(PI_ID);
  }

  @Test
  void duplicateEventIsSkipped() throws Exception {
    when(paymentGateway.verifyWebhook(anyString(), any()))
        .thenReturn(
            new PaymentGatewayEvent(
                EVENT_ID, PI_ID, PaymentGatewayEvent.Status.SUCCEEDED, "STRIPE"));
    doThrow(new DataIntegrityViolationException("dup")).when(processedEvents).save(any());

    mockMvc
        .perform(
            post("/v1/payments/webhooks/stripe")
                .header("Stripe-Signature", "t=0,v1=x")
                .content("{}"))
        .andExpect(status().isOk());
    verify(paymentService, never()).confirmByProviderPaymentId(anyString());
  }

  @Test
  void unknownProviderPaymentIdReturns202() throws Exception {
    String unknownId = UUID.randomUUID().toString();
    when(paymentGateway.verifyWebhook(anyString(), any()))
        .thenReturn(
            new PaymentGatewayEvent(
                EVENT_ID, unknownId, PaymentGatewayEvent.Status.SUCCEEDED, "STRIPE"));
    when(paymentService.confirmByProviderPaymentId(unknownId)).thenReturn(null);

    mockMvc
        .perform(
            post("/v1/payments/webhooks/stripe")
                .header("Stripe-Signature", "t=0,v1=x")
                .content("{}"))
        .andExpect(status().isAccepted());
  }
}
