package com.asoviewclone.analytics.config;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.AckMode;
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;

/** Wires Pub/Sub subscriptions to Spring Integration channels for each event topic. */
@Configuration
public class PubSubSubscriptionConfig {

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
