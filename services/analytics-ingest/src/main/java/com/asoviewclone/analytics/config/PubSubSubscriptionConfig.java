package com.asoviewclone.analytics.config;

import com.google.cloud.spring.pubsub.core.PubSubConfiguration;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.AckMode;
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import com.google.cloud.spring.pubsub.support.DefaultPublisherFactory;
import com.google.cloud.spring.pubsub.support.DefaultSubscriberFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;

/**
 * Wires Pub/Sub subscriptions to Spring Integration channels for each event topic. Only activates
 * when spring.cloud.gcp.project-id is set (not in test/local without GCP).
 */
@Configuration
@ConditionalOnProperty("spring.cloud.gcp.project-id")
public class PubSubSubscriptionConfig {

  @Bean
  public PubSubTemplate pubSubTemplate(@Value("${spring.cloud.gcp.project-id}") String projectId) {
    DefaultPublisherFactory publisherFactory = new DefaultPublisherFactory(() -> projectId);
    DefaultSubscriberFactory subscriberFactory =
        new DefaultSubscriberFactory(() -> projectId, new PubSubConfiguration());
    return new PubSubTemplate(publisherFactory, subscriberFactory);
  }

  @Bean
  public MessageChannel orderEventInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public PubSubInboundChannelAdapter orderEventAdapter(
      @Qualifier("orderEventInputChannel") MessageChannel channel, PubSubTemplate pubSubTemplate) {
    PubSubInboundChannelAdapter adapter =
        new PubSubInboundChannelAdapter(pubSubTemplate, "order-events-analytics-sub");
    adapter.setOutputChannel(channel);
    adapter.setAckMode(AckMode.MANUAL);
    adapter.setPayloadType(byte[].class);
    return adapter;
  }

  @Bean
  public MessageChannel paymentEventInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public PubSubInboundChannelAdapter paymentEventAdapter(
      @Qualifier("paymentEventInputChannel") MessageChannel channel,
      PubSubTemplate pubSubTemplate) {
    PubSubInboundChannelAdapter adapter =
        new PubSubInboundChannelAdapter(pubSubTemplate, "payment-events-analytics-sub");
    adapter.setOutputChannel(channel);
    adapter.setAckMode(AckMode.MANUAL);
    adapter.setPayloadType(byte[].class);
    return adapter;
  }
}
