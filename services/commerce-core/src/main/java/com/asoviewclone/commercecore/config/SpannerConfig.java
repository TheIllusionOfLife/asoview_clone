package com.asoviewclone.commercecore.config;

import com.google.cloud.spanner.DatabaseAdminClient;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpannerConfig {

  @Value("${spring.cloud.gcp.spanner.project-id}")
  private String projectId;

  @Value("${spring.cloud.gcp.spanner.instance-id}")
  private String instanceId;

  @Value("${spring.cloud.gcp.spanner.database}")
  private String databaseName;

  @Value("${spring.cloud.gcp.spanner.emulator-host:}")
  private String emulatorHostProperty;

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  public Spanner spanner() {
    SpannerOptions.Builder builder = SpannerOptions.newBuilder().setProjectId(projectId);
    String emulatorHost = System.getenv("SPANNER_EMULATOR_HOST");
    if (emulatorHost != null) {
      builder.setEmulatorHost(emulatorHost);
    } else if (emulatorHostProperty != null && !emulatorHostProperty.isEmpty()) {
      builder.setEmulatorHost(emulatorHostProperty);
    }
    return builder.build().getService();
  }

  @Bean
  @ConditionalOnMissingBean
  public DatabaseClient databaseClient(Spanner spanner) {
    return spanner.getDatabaseClient(DatabaseId.of(projectId, instanceId, databaseName));
  }

  @Bean
  @ConditionalOnMissingBean
  public DatabaseAdminClient databaseAdminClient(Spanner spanner) {
    return spanner.getDatabaseAdminClient();
  }
}
