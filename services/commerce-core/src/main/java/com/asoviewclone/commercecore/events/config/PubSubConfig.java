package com.asoviewclone.commercecore.events.config;

import com.google.cloud.spring.pubsub.core.PubSubConfiguration;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.DefaultPublisherFactory;
import com.google.cloud.spring.pubsub.support.DefaultSubscriberFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Manual Pub/Sub bean wiring. GcpPubSubAutoConfiguration is excluded from the main Application
 * (same pattern as Spanner) because it fails without GCP project ID in local/test environments.
 * This config only activates when spring.cloud.gcp.project-id is set to a non-empty value.
 *
 * <p>Uses {@code @ConditionalOnExpression} with an explicit non-empty check rather than plain
 * {@code @ConditionalOnProperty}. Now that {@code application-dev.yml} anchors {@code
 * spring.cloud.gcp.project-id} with an empty-string default (so the property always exists in the
 * Environment), a plain {@code @ConditionalOnProperty} without {@code havingValue} would match on
 * empty — which would register {@link PubSubTemplate} with an empty project-id and fail at first
 * publish. The SpEL comparison skips activation when unset, matching the same pattern used by
 * {@code GcpPubSubPublisher} and {@code ProductUpsertedSubscriber}.
 */
@Configuration
@ConditionalOnExpression("'${spring.cloud.gcp.project-id:}' != ''")
public class PubSubConfig {

  @Bean
  public PubSubTemplate pubSubTemplate(@Value("${spring.cloud.gcp.project-id}") String projectId) {
    DefaultPublisherFactory publisherFactory = new DefaultPublisherFactory(() -> projectId);
    PubSubConfiguration config = new PubSubConfiguration();
    config.initialize(projectId);
    DefaultSubscriberFactory subscriberFactory =
        new DefaultSubscriberFactory(() -> projectId, config);
    return new PubSubTemplate(publisherFactory, subscriberFactory);
  }
}
