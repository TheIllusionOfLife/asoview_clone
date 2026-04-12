package com.asoviewclone.analytics;

import com.google.cloud.bigquery.BigQuery;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ApplicationTest {

  @MockitoBean BigQuery bigQuery;

  @Test
  void contextLoads() {}
}
