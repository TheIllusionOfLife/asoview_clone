package com.asoviewclone.searchservice.config;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "search.popularity-sync.enabled", havingValue = "true")
public class BigQueryConfig {

  @Bean
  public BigQuery bigQuery() {
    return BigQueryOptions.getDefaultInstance().getService();
  }
}
