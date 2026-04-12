package com.asoviewclone.analytics.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.asoviewclone.analytics.writer.BigQueryWriterService;
import com.asoviewclone.proto.analytics.v1.EventMetadata;
import com.asoviewclone.proto.analytics.v1.OrderEvent;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders;
import com.google.protobuf.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;

class OrderEventSubscriberTest {

  private final BigQueryWriterService writer = mock(BigQueryWriterService.class);
  private final OrderEventSubscriber subscriber = new OrderEventSubscriber(writer);

  @Test
  void handle_validOrderEvent_writesToBigQuery() {
    OrderEvent event =
        OrderEvent.newBuilder()
            .setMetadata(
                EventMetadata.newBuilder()
                    .setEventId("evt-1")
                    .setEventType("order.paid")
                    .setProducer("commerce-core")
                    .setOccurredAt(Timestamp.newBuilder().setSeconds(1000).build()))
            .setOrderId("order-1")
            .setUserId("user-1")
            .setStatus("PAID")
            .setTotalAmountJpy(5000)
            .setCurrency("JPY")
            .build();

    BasicAcknowledgeablePubsubMessage ack = mock(BasicAcknowledgeablePubsubMessage.class);

    subscriber.handle(
        MessageBuilder.withPayload(event.toByteArray())
            .setHeader(GcpPubSubHeaders.ORIGINAL_MESSAGE, ack)
            .build());

    verify(writer).insert(eq("order_events"), eq("evt-1"), any(Map.class));
    verify(ack).ack();
  }

  @Test
  void handle_invalidProto_nacks() {
    BasicAcknowledgeablePubsubMessage ack = mock(BasicAcknowledgeablePubsubMessage.class);

    subscriber.handle(
        MessageBuilder.withPayload(new byte[] {0x00, 0x01, 0x02})
            .setHeader(GcpPubSubHeaders.ORIGINAL_MESSAGE, ack)
            .build());

    verify(writer, never()).insert(any(), any(), any());
    verify(ack).nack();
  }
}
