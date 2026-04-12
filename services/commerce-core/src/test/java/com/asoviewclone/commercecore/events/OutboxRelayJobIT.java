package com.asoviewclone.commercecore.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import com.asoviewclone.commercecore.events.model.OutboxEvent;
import com.asoviewclone.commercecore.events.repository.OutboxEventRepository;
import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for OutboxRelayJob using real Postgres persistence. Verifies that unpublished
 * outbox rows are published and marked with a non-null publishedAt timestamp.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({
  PostgresContainerConfig.class,
  RedisContainerConfig.class,
  SpannerEmulatorConfig.class,
  OutboxRelayJobIT.StubPublisherConfig.class
})
class OutboxRelayJobIT {

  @TestConfiguration
  static class StubPublisherConfig {
    @Bean
    public PubSubPublisher pubSubPublisher() {
      PubSubPublisher mock = org.mockito.Mockito.mock(PubSubPublisher.class);
      doNothing().when(mock).publish(any(), any(), any());
      return mock;
    }
  }

  @Autowired private OutboxEventRepository outboxRepository;
  @Autowired private PubSubPublisher publisher;

  @Test
  void relay_publishesAndMarksRows() {
    OutboxEvent e1 =
        new OutboxEvent("evt-it-1", "order.paid", "order-1", "order-events", new byte[] {1});
    OutboxEvent e2 =
        new OutboxEvent("evt-it-2", "payment.created", "order-2", "payment-events", new byte[] {2});
    outboxRepository.saveAllAndFlush(List.of(e1, e2));

    assertThat(outboxRepository.findUnpublished(100)).hasSize(2);

    OutboxRelayJob job = new OutboxRelayJob(outboxRepository, publisher);
    job.relay();

    List<OutboxEvent> remaining = outboxRepository.findUnpublished(100);
    assertThat(remaining).isEmpty();

    OutboxEvent published1 = outboxRepository.findById(e1.getId()).orElseThrow();
    assertThat(published1.getPublishedAt()).isNotNull();

    OutboxEvent published2 = outboxRepository.findById(e2.getId()).orElseThrow();
    assertThat(published2.getPublishedAt()).isNotNull();
  }
}
