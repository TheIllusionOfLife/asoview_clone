package com.asoviewclone.analytics;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ApplicationTest {

  @MockitoBean BigQuery bigQuery;
  @MockitoBean PubSubTemplate pubSubTemplate;

  @Test
  void contextLoads() {}
}
