package com.asoviewclone.commercecore.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.asoviewclone.commercecore.events.model.OutboxEvent;
import com.asoviewclone.commercecore.events.repository.OutboxEventRepository;
import com.asoviewclone.commercecore.orders.event.OrderCancelledEvent;
import com.asoviewclone.commercecore.orders.event.OrderPaidEvent;
import com.asoviewclone.commercecore.payments.event.PaymentCreatedEvent;
import com.asoviewclone.proto.analytics.v1.OrderEvent;
import com.asoviewclone.proto.analytics.v1.PaymentEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class OutboxEventListenerTest {

  private final OutboxEventRepository repo = Mockito.mock(OutboxEventRepository.class);
  private final OutboxEventListener listener = new OutboxEventListener(repo);

  @Test
  void onOrderPaid_writesOutboxRow() throws Exception {
    Mockito.when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    listener.onOrderPaid(new OrderPaidEvent("order-1", "user-1", 5000L));

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repo).saveAndFlush(captor.capture());

    OutboxEvent saved = captor.getValue();
    assertThat(saved.getEventId()).isNotBlank();
    assertThat(saved.getEventType()).isEqualTo("order.paid");
    assertThat(saved.getAggregateId()).isEqualTo("order-1");
    assertThat(saved.getTopic()).isEqualTo("order-events");
    assertThat(saved.getPublishedAt()).isNull();

    OrderEvent proto = OrderEvent.parseFrom(saved.getPayload());
    assertThat(proto.getOrderId()).isEqualTo("order-1");
    assertThat(proto.getUserId()).isEqualTo("user-1");
    assertThat(proto.getStatus()).isEqualTo("PAID");
    assertThat(proto.getTotalAmountJpy()).isEqualTo(5000L);
    assertThat(proto.getMetadata().getEventType()).isEqualTo("order.paid");
    assertThat(proto.getMetadata().getProducer()).isEqualTo("commerce-core");
    assertThat(proto.getMetadata().getEventId()).isEqualTo(saved.getEventId());
  }

  @Test
  void onOrderCancelled_writesOutboxRow() throws Exception {
    Mockito.when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    listener.onOrderCancelled(new OrderCancelledEvent("order-2", "user-2"));

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repo).saveAndFlush(captor.capture());

    OutboxEvent saved = captor.getValue();
    assertThat(saved.getEventType()).isEqualTo("order.cancelled");
    assertThat(saved.getTopic()).isEqualTo("order-events");

    OrderEvent proto = OrderEvent.parseFrom(saved.getPayload());
    assertThat(proto.getStatus()).isEqualTo("CANCELLED");
  }

  @Test
  void onPaymentCreated_writesOutboxRow() throws Exception {
    Mockito.when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    listener.onPaymentCreated(new PaymentCreatedEvent("order-3", "pay-1", 3000L, "stripe"));

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repo).saveAndFlush(captor.capture());

    OutboxEvent saved = captor.getValue();
    assertThat(saved.getEventType()).isEqualTo("payment.created");
    assertThat(saved.getTopic()).isEqualTo("payment-events");

    PaymentEvent proto = PaymentEvent.parseFrom(saved.getPayload());
    assertThat(proto.getOrderId()).isEqualTo("order-3");
    assertThat(proto.getPaymentId()).isEqualTo("pay-1");
    assertThat(proto.getAmountJpy()).isEqualTo(3000L);
    assertThat(proto.getProvider()).isEqualTo("stripe");
    assertThat(proto.getStatus()).isEqualTo("PROCESSING");
  }
}
