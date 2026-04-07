package com.asoviewclone.commercecore.payments.webhook;

import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.model.PaymentGatewayEvent;
import com.asoviewclone.commercecore.payments.service.PaymentGateway;
import com.asoviewclone.commercecore.payments.service.PaymentService;
import com.asoviewclone.common.error.ConflictException;
import com.asoviewclone.common.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PayPay webhook ingress. Mirrors {@link PaymentWebhookController} for the PayPay provider. The
 * signature header is {@code X-PAYPAY-Signature} (HMAC-SHA256 hex over the raw body). Replay
 * protection, terminal-vs-transient conflict classification, and out-of-order guards follow the
 * same rules as the Stripe handler. See CLAUDE.md "Review Pitfalls (PR #19)" for the rationale.
 *
 * <p>The controller method is intentionally NOT {@code @Transactional}: marking it transactional
 * caused {@code ConflictException} to flip the outer JPA tx to rollback-only, which then rolled
 * back the {@code processedEvents.deleteById} cleanup in the catch block — Spring throws {@code
 * UnexpectedRollbackException} returning 500, PayPay retries forever. The downstream {@link
 * PaymentService#confirmByProviderPaymentId} method already opens its own transaction; the proxy
 * self-call concern is handled inside that service method, not at the controller boundary. (PR #21
 * review C3 from Devin.)
 */
@RestController
@RequestMapping("/v1/payments/webhooks")
@ConditionalOnProperty(name = "payments.gateway", havingValue = "paypay")
public class PayPayWebhookController {

  private static final Logger log = LoggerFactory.getLogger(PayPayWebhookController.class);

  private final PaymentGateway paymentGateway;
  private final PaymentService paymentService;
  private final ProcessedWebhookEventRepository processedEvents;

  public PayPayWebhookController(
      PaymentGateway paymentGateway,
      PaymentService paymentService,
      ProcessedWebhookEventRepository processedEvents) {
    this.paymentGateway = paymentGateway;
    this.paymentService = paymentService;
    this.processedEvents = processedEvents;
  }

  @PostMapping("/paypay")
  public ResponseEntity<String> handlePayPay(
      @RequestHeader(value = "X-PAYPAY-Signature", required = false) String signature,
      @RequestBody byte[] rawBody) {
    PaymentGatewayEvent event;
    try {
      event = paymentGateway.verifyWebhook(signature, rawBody);
    } catch (ValidationException e) {
      log.warn("PayPay webhook signature verification failed: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("signature");
    }

    if (event.status() == PaymentGatewayEvent.Status.IGNORED) {
      log.debug("Ignoring PayPay webhook event id={}", event.rawEventId());
      return ResponseEntity.ok("ignored");
    }

    // Replay protection: insert marker row before doing any work. Composite
    // PK on (provider, event_id) from V9 keeps Stripe and PayPay event ids
    // isolated so there's no cross-provider collision risk.
    // Insert-first idempotency gate. ProcessedWebhookEvent has assigned @Id fields, so
    // save() would route through merge() (SELECT + UPDATE) for sequential retries and never
    // throw DataIntegrityViolationException — defeating replay protection. Native ON CONFLICT
    // DO NOTHING is atomic against the unique key. (Devin PR #22 finding.)
    if (processedEvents.insertIfMissing(event.provider(), event.rawEventId()) == 0) {
      log.info(
          "Duplicate PayPay webhook replay event_id={} provider_payment_id={} action=skip",
          event.rawEventId(),
          event.providerPaymentId());
      return ResponseEntity.ok("duplicate");
    }

    try {
      if (event.status() == PaymentGatewayEvent.Status.SUCCEEDED) {
        Payment result = paymentService.confirmByProviderPaymentId(event.providerPaymentId());
        if (result == null) {
          // Unknown merchantPaymentId — delete the replay row so the next
          // delivery can retry (PR #19 guard-row cleanup rule).
          processedEvents.deleteById(
              new ProcessedWebhookEventId(event.provider(), event.rawEventId()));
          return ResponseEntity.status(HttpStatus.ACCEPTED).body("unknown");
        }
        log.info(
            "PayPay webhook confirmed event_id={} provider_payment_id={} order_id={} action=succeed",
            event.rawEventId(),
            event.providerPaymentId(),
            result.getOrderId());
        return ResponseEntity.ok("confirmed");
      }
      Payment result = paymentService.failByProviderPaymentId(event.providerPaymentId());
      if (result == null) {
        processedEvents.deleteById(
            new ProcessedWebhookEventId(event.provider(), event.rawEventId()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("unknown");
      }
      log.info(
          "PayPay webhook failed event_id={} provider_payment_id={} order_id={} action=fail",
          event.rawEventId(),
          event.providerPaymentId(),
          result.getOrderId());
      return ResponseEntity.ok("failed");
    } catch (ConflictException e) {
      boolean terminal = paymentService.isTerminalConflict(event.providerPaymentId());
      if (terminal) {
        log.info(
            "PayPay webhook conflict resolved as terminal event_id={} provider_payment_id={}",
            event.rawEventId(),
            event.providerPaymentId());
        // Terminal: keep replay row so provider stops retrying.
        return ResponseEntity.ok("terminal");
      }
      log.warn(
          "PayPay webhook transient conflict event_id={} provider_payment_id={} reason={}",
          event.rawEventId(),
          event.providerPaymentId(),
          e.getMessage());
      // Transient: delete the replay row so the next delivery can re-enter.
      processedEvents.deleteById(new ProcessedWebhookEventId(event.provider(), event.rawEventId()));
      return ResponseEntity.status(HttpStatus.ACCEPTED).body("conflict");
    } catch (RuntimeException unexpected) {
      // Any uncaught exception below ConflictException would otherwise leave
      // the replay marker in place forever, causing every retry from PayPay
      // to be silently dropped as a duplicate. Delete the marker so the
      // provider can retry, then re-throw so the 5xx propagates and the
      // failure is observable. (PR #21 review follow-up.)
      log.error(
          "PayPay webhook unexpected error event_id={} provider_payment_id={}; releasing replay marker",
          event.rawEventId(),
          event.providerPaymentId(),
          unexpected);
      try {
        processedEvents.deleteById(
            new ProcessedWebhookEventId(event.provider(), event.rawEventId()));
      } catch (Exception cleanup) {
        log.warn(
            "PayPay webhook replay marker cleanup also failed event_id={}; manual repair needed",
            event.rawEventId(),
            cleanup);
      }
      throw unexpected;
    }
  }
}
