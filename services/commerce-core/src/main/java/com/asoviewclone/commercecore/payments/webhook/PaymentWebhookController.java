package com.asoviewclone.commercecore.payments.webhook;

import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.model.PaymentGatewayEvent;
import com.asoviewclone.commercecore.payments.service.PaymentGateway;
import com.asoviewclone.commercecore.payments.service.PaymentService;
import com.asoviewclone.common.error.ConflictException;
import com.asoviewclone.common.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingress for provider-sent webhook events. The path {@code /v1/payments/webhooks/**} must be
 * excluded from the Firebase auth filter (see {@code SecurityConfig}). The raw request body is
 * mapped directly to {@code byte[]} so signature verification sees the exact bytes the provider
 * signed.
 *
 * <p>Replay protection: Stripe can deliver the same event multiple times. We insert a row in {@code
 * processed_webhook_events} keyed by the provider event id; a unique-constraint violation on a
 * retried delivery is caught and returned as {@code 200 OK}.
 *
 * <p>Race with AFTER_COMMIT listener: Stripe may deliver {@code payment_intent.succeeded} before
 * {@code PaymentCreatedEventListener} has advanced the order from PENDING to PAYMENT_PENDING.
 * {@code PaymentServiceImpl.confirmByProviderPaymentId} handles this by rolling the order forward
 * synchronously before calling {@code confirmPayment}. If that roll-forward loses a CAS (e.g.,
 * order is in an unexpected state) we return 202 so the provider retries.
 */
@RestController
@RequestMapping("/v1/payments/webhooks")
public class PaymentWebhookController {

  private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

  private final PaymentGateway paymentGateway;
  private final PaymentService paymentService;
  private final ProcessedWebhookEventRepository processedEvents;

  public PaymentWebhookController(
      PaymentGateway paymentGateway,
      PaymentService paymentService,
      ProcessedWebhookEventRepository processedEvents) {
    this.paymentGateway = paymentGateway;
    this.paymentService = paymentService;
    this.processedEvents = processedEvents;
  }

  @PostMapping("/stripe")
  public ResponseEntity<String> handleStripe(
      @RequestHeader(value = "Stripe-Signature", required = false) String signature,
      @RequestBody byte[] rawBody) {
    return handle(signature, rawBody);
  }

  /**
   * Single shared handler for any provider we wire up later (PayPay, etc.). Keeping it collapsed
   * means the replay, race, and logging logic lives in exactly one place.
   */
  private ResponseEntity<String> handle(String signature, byte[] rawBody) {
    PaymentGatewayEvent event;
    try {
      event = paymentGateway.verifyWebhook(signature, rawBody);
    } catch (ValidationException e) {
      log.warn("Webhook signature verification failed: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("signature");
    }

    if (event.status() == PaymentGatewayEvent.Status.IGNORED) {
      log.debug(
          "Ignoring webhook event stripe_event_id={} provider={}",
          event.rawEventId(),
          event.provider());
      return ResponseEntity.ok("ignored");
    }

    // Replay protection: insert first, then act. If the event id is a duplicate the unique
    // constraint rejects the insert and we return 200 without re-running the confirm/fail logic.
    // Insert-first idempotency gate. ProcessedWebhookEvent has assigned @Id fields, so
    // save() would route through merge() (SELECT + UPDATE) for sequential retries and never
    // throw DataIntegrityViolationException — defeating replay protection. Native ON CONFLICT
    // DO NOTHING is atomic against the unique key. (Devin PR #22 finding.)
    if (processedEvents.insertIfMissing(event.provider(), event.rawEventId()) == 0) {
      log.info(
          "Duplicate webhook replay stripe_event_id={} provider_payment_id={} action=skip",
          event.rawEventId(),
          event.providerPaymentId());
      return ResponseEntity.ok("duplicate");
    }

    try {
      if (event.status() == PaymentGatewayEvent.Status.SUCCEEDED) {
        Payment result = paymentService.confirmByProviderPaymentId(event.providerPaymentId());
        if (result == null) {
          log.warn(
              "Unknown provider_payment_id={} on success; returning 202 for retry",
              event.providerPaymentId());
          // Undo the replay guard: Stripe will retry this event and the service
          // may know about the payment by then (the PaymentCreatedEvent
          // AFTER_COMMIT listener may still be in-flight). If we leave the row
          // in place, the next delivery hits the duplicate check and is
          // silently dropped forever.
          processedEvents.deleteById(
              new ProcessedWebhookEventId(event.provider(), event.rawEventId()));
          return ResponseEntity.status(HttpStatus.ACCEPTED).body("unknown");
        }
        log.info(
            "Webhook confirmed stripe_event_id={} provider_payment_id={} order_id={} action=succeed result=ok",
            event.rawEventId(),
            event.providerPaymentId(),
            result.getOrderId());
        return ResponseEntity.ok("confirmed");
      }
      // FAILED path
      Payment result = paymentService.failByProviderPaymentId(event.providerPaymentId());
      if (result == null) {
        // Same rationale as above: undo the replay row so Stripe's retry can proceed.
        processedEvents.deleteById(
            new ProcessedWebhookEventId(event.provider(), event.rawEventId()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("unknown");
      }
      log.info(
          "Webhook failed stripe_event_id={} provider_payment_id={} order_id={} action=fail result=ok",
          event.rawEventId(),
          event.providerPaymentId(),
          result.getOrderId());
      return ResponseEntity.ok("failed");
    } catch (ConflictException e) {
      // Distinguish terminal from transient conflict via the service helper.
      // Terminal = the payment or its order is in a state that no retry can
      // recover from (payment SUCCEEDED/FAILED, or order CANCELLED/PAID/
      // REFUNDED). Terminal conflicts must ACK 200 so the provider stops
      // retrying; transient conflicts return 202 so the next delivery can
      // re-enter. The controller does not inspect order state directly — it
      // defers to paymentService.isTerminalConflict which owns the
      // classification rules.
      boolean terminal = paymentService.isTerminalConflict(event.providerPaymentId());
      if (terminal) {
        log.info(
            "Webhook conflict resolved as terminal stripe_event_id={} provider_payment_id={} action=ack",
            event.rawEventId(),
            event.providerPaymentId());
        return ResponseEntity.ok("terminal");
      }
      log.warn(
          "Webhook conflict stripe_event_id={} provider_payment_id={} reason={}",
          event.rawEventId(),
          event.providerPaymentId(),
          e.getMessage());
      // Undo the replay guard so a genuine retry can proceed.
      processedEvents.deleteById(new ProcessedWebhookEventId(event.provider(), event.rawEventId()));
      return ResponseEntity.status(HttpStatus.ACCEPTED).body("conflict");
    } catch (RuntimeException unexpected) {
      // Any uncaught exception below would otherwise leave the replay marker
      // in place forever, causing every Stripe retry to be silently dropped
      // as a duplicate. Delete the marker so the provider can retry, then
      // re-throw so the 5xx propagates and the failure is observable.
      // Mirrors the equivalent block in PayPayWebhookController.
      log.error(
          "Stripe webhook unexpected error stripe_event_id={} provider_payment_id={}; releasing replay marker",
          event.rawEventId(),
          event.providerPaymentId(),
          unexpected);
      try {
        processedEvents.deleteById(
            new ProcessedWebhookEventId(event.provider(), event.rawEventId()));
      } catch (Exception cleanup) {
        log.warn(
            "Stripe webhook replay marker cleanup also failed stripe_event_id={}; manual repair needed",
            event.rawEventId(),
            cleanup);
      }
      throw unexpected;
    }
  }
}
