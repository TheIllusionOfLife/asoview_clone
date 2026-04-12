package com.asoviewclone.analytics.config;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BigQueryConfig {

  @Bean
  @ConditionalOnMissingBean
  public BigQuery bigQuery() {
    return BigQueryOptions.getDefaultInstance().getService();
  }
}
