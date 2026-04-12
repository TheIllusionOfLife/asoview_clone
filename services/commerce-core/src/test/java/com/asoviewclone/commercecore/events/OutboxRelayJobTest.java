package com.asoviewclone.commercecore.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asoviewclone.commercecore.events.model.OutboxEvent;
import com.asoviewclone.commercecore.events.repository.OutboxEventRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboxRelayJobTest {

  private final OutboxEventRepository repo = mock(OutboxEventRepository.class);
  private final PubSubPublisher publisher = mock(PubSubPublisher.class);
  private final OutboxRelayJob job = new OutboxRelayJob(repo, publisher);

  @Test
  void relay_publishesAndMarksPublished() {
    OutboxEvent event = new OutboxEvent("order.paid", "order-1", "order-events", new byte[] {1, 2});
    when(repo.findUnpublished()).thenReturn(List.of(event));
    when(repo.markPublished(event.getId())).thenReturn(1);

    job.relay();

    verify(publisher).publish(eq("order-events"), eq(event.getId().toString()), any(byte[].class));
    verify(repo).markPublished(event.getId());
  }

  @Test
  void relay_publisherFailure_doesNotMarkPublished() {
    OutboxEvent event = new OutboxEvent("order.paid", "order-1", "order-events", new byte[] {1, 2});
    when(repo.findUnpublished()).thenReturn(List.of(event));
    doThrow(new RuntimeException("Pub/Sub down")).when(publisher).publish(any(), any(), any());

    job.relay();

    verify(repo, never()).markPublished(any());
  }

  @Test
  void relay_emptyQueue_noOp() {
    when(repo.findUnpublished()).thenReturn(List.of());

    job.relay();

    verify(publisher, never()).publish(any(), any(), any());
  }
}
