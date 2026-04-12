package com.asoviewclone.commercecore.events;

import com.asoviewclone.commercecore.events.model.OutboxEvent;
import com.asoviewclone.commercecore.events.repository.OutboxEventRepository;
import com.asoviewclone.commercecore.orders.event.OrderCancelledEvent;
import com.asoviewclone.commercecore.orders.event.OrderPaidEvent;
import com.asoviewclone.commercecore.payments.event.PaymentCreatedEvent;
import com.asoviewclone.proto.analytics.v1.EventMetadata;
import com.asoviewclone.proto.analytics.v1.OrderEvent;
import com.asoviewclone.proto.analytics.v1.PaymentEvent;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes outbox rows for domain events within the same transaction (BEFORE_COMMIT). The relay job
 * picks them up and publishes to Pub/Sub asynchronously.
 */
@Component
public class OutboxEventListener {

  private static final Logger log = LoggerFactory.getLogger(OutboxEventListener.class);
  private static final String PRODUCER = "commerce-core";

  private final OutboxEventRepository outboxRepository;

  public OutboxEventListener(OutboxEventRepository outboxRepository) {
    this.outboxRepository = outboxRepository;
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onOrderPaid(OrderPaidEvent event) {
    String eventId = UUID.randomUUID().toString();
    OrderEvent proto =
        OrderEvent.newBuilder()
            .setMetadata(metadata(eventId, "order.paid", 1, event.orderId(), Instant.now()))
            .setOrderId(event.orderId())
            .setUserId(event.userId())
            .setStatus("PAID")
            .setTotalAmountJpy(event.subtotalJpy())
            .setCurrency("JPY")
            .build();

    outboxRepository.saveAndFlush(
        new OutboxEvent("order.paid", event.orderId(), "order-events", proto.toByteArray()));
    log.debug("Outbox: order.paid for order {}", event.orderId());
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onOrderCancelled(OrderCancelledEvent event) {
    String eventId = UUID.randomUUID().toString();
    OrderEvent proto =
        OrderEvent.newBuilder()
            .setMetadata(metadata(eventId, "order.cancelled", 1, event.orderId(), Instant.now()))
            .setOrderId(event.orderId())
            .setUserId(event.userId())
            .setStatus("CANCELLED")
            .setCurrency("JPY")
            .build();

    outboxRepository.saveAndFlush(
        new OutboxEvent("order.cancelled", event.orderId(), "order-events", proto.toByteArray()));
    log.debug("Outbox: order.cancelled for order {}", event.orderId());
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onPaymentCreated(PaymentCreatedEvent event) {
    String eventId = UUID.randomUUID().toString();
    PaymentEvent proto =
        PaymentEvent.newBuilder()
            .setMetadata(metadata(eventId, "payment.created", 1, event.orderId(), Instant.now()))
            .setOrderId(event.orderId())
            .setStatus("PROCESSING")
            .setCurrency("JPY")
            .build();

    outboxRepository.saveAndFlush(
        new OutboxEvent("payment.created", event.orderId(), "payment-events", proto.toByteArray()));
    log.debug("Outbox: payment.created for order {}", event.orderId());
  }

  private static EventMetadata metadata(
      String eventId, String eventType, int version, String aggregateId, Instant now) {
    return EventMetadata.newBuilder()
        .setEventId(eventId)
        .setEventType(eventType)
        .setEventVersion(version)
        .setAggregateId(aggregateId)
        .setTraceId(UUID.randomUUID().toString())
        .setProducer(PRODUCER)
        .setOccurredAt(
            Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build())
        .build();
  }
}
