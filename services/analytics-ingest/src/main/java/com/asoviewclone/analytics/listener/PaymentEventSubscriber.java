package com.asoviewclone.analytics.listener;

import com.asoviewclone.analytics.writer.BigQueryWriterService;
import com.asoviewclone.proto.analytics.v1.PaymentEvent;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/** Subscribes to the payment-events Pub/Sub topic and writes rows to BigQuery. */
@Component
public class PaymentEventSubscriber {

  private static final Logger log = LoggerFactory.getLogger(PaymentEventSubscriber.class);

  private final BigQueryWriterService writer;

  public PaymentEventSubscriber(BigQueryWriterService writer) {
    this.writer = writer;
  }

  @ServiceActivator(inputChannel = "paymentEventInputChannel")
  public void handle(Message<byte[]> message) {
    BasicAcknowledgeablePubsubMessage ack =
        message
            .getHeaders()
            .get(GcpPubSubHeaders.ORIGINAL_MESSAGE, BasicAcknowledgeablePubsubMessage.class);
    try {
      PaymentEvent event = PaymentEvent.parseFrom(message.getPayload());
      String eventId = event.getMetadata().getEventId();

      Map<String, Object> row = new LinkedHashMap<>();
      row.put("event_id", eventId);
      row.put("event_type", event.getMetadata().getEventType());
      row.put("payment_id", event.getPaymentId());
      row.put("order_id", event.getOrderId());
      row.put("status", event.getStatus());
      row.put("provider", event.getProvider());
      row.put("amount_jpy", event.getAmountJpy());
      row.put("currency", event.getCurrency());
      row.put(
          "occurred_at",
          Instant.ofEpochSecond(
                  event.getMetadata().getOccurredAt().getSeconds(),
                  event.getMetadata().getOccurredAt().getNanos())
              .toString());
      row.put("producer", event.getMetadata().getProducer());

      writer.insert("payment_events", eventId, row);
      if (ack != null) {
        ack.ack();
      }
    } catch (InvalidProtocolBufferException e) {
      log.error("Failed to deserialize PaymentEvent, nacking for DLQ", e);
      if (ack != null) {
        ack.nack();
      }
    } catch (Exception e) {
      log.error("Failed to process PaymentEvent", e);
      if (ack != null) {
        ack.nack();
      }
    }
  }
}
