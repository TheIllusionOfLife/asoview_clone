package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.payments.model.PaymentGatewayEvent;
import com.asoviewclone.common.error.ConflictException;
import com.asoviewclone.common.error.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin PayPay gateway implementation that talks directly to the PayPay sandbox REST API via
 * Spring's {@link RestClient}. We intentionally do NOT pull in the official PayPay SDK: it is
 * heavyweight (Retrofit + OkHttp + jackson-dataformat-xml) and we only need two endpoints.
 *
 * <p>Activation: {@code payments.gateway=paypay}. Stripe stays the default via its own
 * {@code @Primary} binding under {@code payments.gateway=stripe}.
 *
 * <p>Webhook signature verification uses HMAC-SHA256 over the raw request body with the shared
 * secret from {@code payments.paypay.webhook-secret}. The body bytes are decoded as UTF-8 for JSON
 * parsing (see CLAUDE.md "Review Pitfalls (PR #19)" for the charset rationale).
 */
@Component("payPayPaymentGateway")
@Primary
@ConditionalOnProperty(name = "payments.gateway", havingValue = "paypay")
public class PayPayPaymentGateway implements PaymentGateway {

  public static final String PROVIDER_NAME = "PAYPAY";

  private static final Logger log = LoggerFactory.getLogger(PayPayPaymentGateway.class);
  private static final String SANDBOX_BASE = "https://stg-api.sandbox.paypay.ne.jp";
  private static final String CREATE_CODE_PATH = "/v2/codes";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${payments.paypay.api-key:}")
  private String apiKey;

  @Value("${payments.paypay.api-secret:}")
  private String apiSecret;

  @Value("${payments.paypay.merchant-id:}")
  private String merchantId;

  @Value("${payments.paypay.webhook-secret:}")
  private String webhookSecret;

  @Value("${payments.paypay.base-url:" + SANDBOX_BASE + "}")
  private String baseUrl;

  private RestClient client;

  @PostConstruct
  void init() {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "payments.paypay.api-key must be set when payments.gateway=paypay");
    }
    if (apiSecret == null || apiSecret.isBlank()) {
      throw new IllegalStateException(
          "payments.paypay.api-secret must be set when payments.gateway=paypay");
    }
    if (webhookSecret == null || webhookSecret.isBlank()) {
      throw new IllegalStateException(
          "payments.paypay.webhook-secret must be set when payments.gateway=paypay");
    }
    this.client = RestClient.builder().baseUrl(baseUrl).build();
  }

  @Override
  public String providerName() {
    return PROVIDER_NAME;
  }

  @Override
  public PaymentResult createIntent(
      String orderId, BigDecimal amount, String currency, String idempotencyKey) {
    // Stable merchantPaymentId per idempotency key so retries do not mint duplicates
    // on the PayPay side (PayPay rejects duplicate merchantPaymentId with 409 which
    // we translate to a ConflictException).
    String merchantPaymentId =
        "mp-" + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));

    Map<String, Object> body =
        Map.of(
            "merchantPaymentId",
            merchantPaymentId,
            "amount",
            Map.of("amount", toMinorUnits(amount, currency), "currency", currency.toUpperCase()),
            "codeType",
            "ORDER_QR",
            "orderDescription",
            "Order " + orderId,
            "isAuthorization",
            false,
            "redirectType",
            "WEB_LINK",
            "requestedAt",
            System.currentTimeMillis() / 1000L);

    try {
      String response =
          client
              .post()
              .uri(CREATE_CODE_PATH)
              .header("X-ASSUME-MERCHANT", merchantId == null ? "" : merchantId)
              .header("Authorization", "hmac OPA-Auth:" + apiKey)
              .header("X-Idempotency-Key", idempotencyKey)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(String.class);

      JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
      JsonNode data = root.path("data");
      String codeId = data.path("codeId").asText(merchantPaymentId);
      // PayPay has no Stripe-style "client secret"; the browser navigates to the
      // provider-hosted QR/checkout URL returned in data.url to complete payment.
      // We expose that URL via PaymentResult.redirectUrl (and the persisted
      // Payment.redirect_url column) and keep merchantPaymentId in the
      // clientSecret slot purely for backward compatibility with callers that
      // already use it as a stable opaque handle.
      String redirectUrl = data.path("url").asText(null);
      return new PaymentResult(codeId, merchantPaymentId, redirectUrl, true);
    } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
      log.warn("PayPay createIntent failed for order {}: {}", orderId, e.getMessage());
      throw new ConflictException("PayPay rejected payment intent creation: " + e.getMessage());
    }
  }

  @Override
  public PaymentGatewayEvent verifyWebhook(String signatureHeader, byte[] rawBody) {
    if (signatureHeader == null || signatureHeader.isBlank()) {
      throw new ValidationException("Missing X-PAYPAY-Signature header");
    }
    String expected = hmacSha256Hex(webhookSecret, rawBody);
    // MessageDigest.isEqual is the JDK's constant-time byte-array compare and
    // handles null inputs and length mismatches without leaking timing info via
    // an early return on length. (PR #21 review follow-up.)
    if (!java.security.MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        signatureHeader.trim().getBytes(StandardCharsets.UTF_8))) {
      throw new ValidationException("PayPay webhook signature verification failed");
    }

    JsonNode root;
    try {
      // Explicit UTF-8 — same rationale as StripePaymentGateway.verifyWebhook.
      root = objectMapper.readTree(new String(rawBody, StandardCharsets.UTF_8));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new ValidationException("PayPay webhook body is not valid JSON: " + e.getMessage());
    }

    String eventId = root.path("notification_id").asText(null);
    if (eventId == null) {
      eventId = root.path("notificationId").asText(null);
    }
    if (eventId == null) {
      throw new ValidationException("PayPay webhook missing notification_id");
    }

    String state = root.path("state").asText("");
    String merchantPaymentId = root.path("merchant_payment_id").asText(null);
    if (merchantPaymentId == null) {
      merchantPaymentId = root.path("merchantPaymentId").asText(null);
    }

    PaymentGatewayEvent.Status mapped;
    if ("COMPLETED".equalsIgnoreCase(state)) {
      mapped = PaymentGatewayEvent.Status.SUCCEEDED;
    } else if ("FAILED".equalsIgnoreCase(state) || "CANCELED".equalsIgnoreCase(state)) {
      mapped = PaymentGatewayEvent.Status.FAILED;
    } else {
      return new PaymentGatewayEvent(
          eventId, merchantPaymentId, PaymentGatewayEvent.Status.IGNORED, PROVIDER_NAME);
    }
    if (merchantPaymentId == null) {
      throw new ValidationException(
          "PayPay webhook missing merchant_payment_id for event " + eventId);
    }
    return new PaymentGatewayEvent(eventId, merchantPaymentId, mapped, PROVIDER_NAME);
  }

  private static String hmacSha256Hex(String secret, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] sig = mac.doFinal(body);
      StringBuilder sb = new StringBuilder(sig.length * 2);
      for (byte b : sig) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new ValidationException("Unable to compute HMAC-SHA256: " + e.getMessage());
    }
  }

  private static long toMinorUnits(BigDecimal amount, String currency) {
    // PayPay only supports JPY (zero-decimal) today.
    if ("JPY".equalsIgnoreCase(currency)) {
      return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
    return amount
        .multiply(BigDecimal.valueOf(100))
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact();
  }
}
