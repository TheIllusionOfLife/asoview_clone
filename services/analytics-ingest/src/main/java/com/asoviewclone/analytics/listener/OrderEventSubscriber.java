package com.asoviewclone.analytics.listener;

import com.asoviewclone.analytics.writer.BigQueryWriterService;
import com.asoviewclone.proto.analytics.v1.OrderEvent;
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

/** Subscribes to the order-events Pub/Sub topic and writes rows to BigQuery. */
@Component
public class OrderEventSubscriber {

  private static final Logger log = LoggerFactory.getLogger(OrderEventSubscriber.class);

  private final BigQueryWriterService writer;

  public OrderEventSubscriber(BigQueryWriterService writer) {
    this.writer = writer;
  }

  @ServiceActivator(inputChannel = "orderEventInputChannel")
  public void handle(Message<byte[]> message) {
    BasicAcknowledgeablePubsubMessage ack =
        message
            .getHeaders()
            .get(GcpPubSubHeaders.ORIGINAL_MESSAGE, BasicAcknowledgeablePubsubMessage.class);
    try {
      OrderEvent event = OrderEvent.parseFrom(message.getPayload());
      String eventId = event.getMetadata().getEventId();

      Map<String, Object> row = new LinkedHashMap<>();
      row.put("event_id", eventId);
      row.put("event_type", event.getMetadata().getEventType());
      row.put("order_id", event.getOrderId());
      row.put("user_id", event.getUserId());
      row.put("status", event.getStatus());
      row.put("subtotal_jpy", event.getSubtotalJpy());
      row.put("currency", event.getCurrency());
      if (!event.getItemsList().isEmpty()) {
        row.put("product_id", event.getItems(0).getProductId());
      }
      row.put(
          "occurred_at",
          Instant.ofEpochSecond(
                  event.getMetadata().getOccurredAt().getSeconds(),
                  event.getMetadata().getOccurredAt().getNanos())
              .toString());
      row.put("producer", event.getMetadata().getProducer());

      writer.insert("order_events", eventId, row);
      if (ack != null) {
        ack.ack();
      }
    } catch (InvalidProtocolBufferException e) {
      log.error("Failed to deserialize OrderEvent, nacking for DLQ", e);
      if (ack != null) {
        ack.nack();
      }
    } catch (Exception e) {
      log.error("Failed to process OrderEvent", e);
      if (ack != null) {
        ack.nack();
      }
    }
  }
}
