package com.asoviewclone.commercecore.payments.saga;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.commercecore.identity.repository.TenantUserRepository;
import com.asoviewclone.commercecore.identity.repository.UserRepository;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.model.PaymentGatewayEvent;
import com.asoviewclone.commercecore.payments.service.FakePayPayGateway;
import com.asoviewclone.commercecore.payments.service.PaymentGateway;
import com.asoviewclone.commercecore.payments.service.PaymentService;
import com.asoviewclone.commercecore.payments.webhook.PayPayWebhookController;
import com.asoviewclone.commercecore.payments.webhook.ProcessedWebhookEventRepository;
import com.google.firebase.auth.FirebaseAuth;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-slice regression that mirrors the Stripe happy-path through the PayPay webhook. The
 * real {@link FakePayPayGateway} produces a deterministic {@link PaymentGatewayEvent}; the service
 * boundary is mocked and we assert the webhook drives {@link
 * PaymentService#confirmByProviderPaymentId} exactly once. This keeps the test hermetic — the full
 * saga is exercised by {@link PaymentConfirmationSagaTest} so duplicating the Spanner /
 * Testcontainers stack here would add minutes of runtime for no additional coverage.
 */
// PayPayWebhookController is @ConditionalOnProperty(payments.gateway=paypay) so the test slice
// has to set that property explicitly; otherwise the controller bean is not registered and
// POST /v1/payments/webhooks/paypay returns 404.
@TestPropertySource(properties = "payments.gateway=paypay")
@WebMvcTest(PayPayWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class PayPaySagaRegressionTest {

  private static final String EVENT_ID = "pp_evt_1";
  private static final String MP_ID = "mp-fake-1";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PaymentGateway paymentGateway;
  @MockitoBean private PaymentService paymentService;
  @MockitoBean private ProcessedWebhookEventRepository processedEvents;
  @MockitoBean private FirebaseAuth firebaseAuth;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private TenantUserRepository tenantUserRepository;

  @Test
  void happyPathConfirmsPaymentAndReturns200() throws Exception {
    when(paymentGateway.verifyWebhook(anyString(), any()))
        .thenReturn(
            new PaymentGatewayEvent(
                EVENT_ID, MP_ID, PaymentGatewayEvent.Status.SUCCEEDED, "PAYPAY"));

    Payment confirmed =
        new Payment(
            UUID.randomUUID().toString(), "user-1", new BigDecimal("3000"), "JPY", "idem-1");
    when(paymentService.confirmByProviderPaymentId(eq(MP_ID))).thenReturn(confirmed);
    when(processedEvents.insertIfMissing("PAYPAY", EVENT_ID)).thenReturn(1);

    mockMvc
        .perform(
            post("/v1/payments/webhooks/paypay")
                .header("X-PAYPAY-Signature", FakePayPayGateway.SECRET)
                .content((EVENT_ID + ":" + MP_ID + ":SUCCEEDED").getBytes()))
        .andExpect(status().isOk());

    verify(processedEvents, times(1)).insertIfMissing("PAYPAY", EVENT_ID);
    verify(paymentService, times(1)).confirmByProviderPaymentId(MP_ID);
  }
}
