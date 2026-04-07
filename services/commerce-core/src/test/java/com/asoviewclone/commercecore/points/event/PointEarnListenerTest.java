package com.asoviewclone.commercecore.points.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.asoviewclone.commercecore.orders.event.OrderPaidEvent;
import com.asoviewclone.commercecore.points.service.PointService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PointEarnListenerTest {

  @Test
  void onOrderPaid_credits1Percent() {
    PointService service = mock(PointService.class);
    PointEarnListener listener = new PointEarnListener(service);
    UUID userId = UUID.randomUUID();

    listener.onOrderPaid(new OrderPaidEvent("order-1", userId.toString(), 10_000L));

    verify(service).earn(userId, 100L, "order-1");
  }

  @Test
  void onOrderPaid_subtotalUnder100_noCredit() {
    PointService service = mock(PointService.class);
    PointEarnListener listener = new PointEarnListener(service);

    listener.onOrderPaid(new OrderPaidEvent("order-1", UUID.randomUUID().toString(), 50L));

    verify(service, never())
        .earn(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any());
  }
}
