package com.asoviewclone.commercecore.testutil;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.NoCredentials;
import com.google.cloud.spanner.DatabaseAdminClient;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.InstanceAdminClient;
import com.google.cloud.spanner.InstanceConfigId;
import com.google.cloud.spanner.InstanceId;
import com.google.cloud.spanner.InstanceInfo;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.SpannerEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class SpannerEmulatorConfig {

  private static final String PROJECT_ID = "test-project";
  private static final String INSTANCE_ID = "test-instance";
  private static final String DATABASE_NAME = "test-database";

  @Bean
  public SpannerEmulatorContainer spannerEmulator() {
    SpannerEmulatorContainer container =
        new SpannerEmulatorContainer(
            DockerImageName.parse("gcr.io/cloud-spanner-emulator/emulator:1.5.28"));
    container.start();

    initializeInstanceAndDatabase(container);
    return container;
  }

  @Bean
  public DynamicPropertyRegistrar spannerProperties(SpannerEmulatorContainer emulator) {
    return registry -> {
      registry.add("spring.cloud.gcp.spanner.project-id", () -> PROJECT_ID);
      registry.add("spring.cloud.gcp.spanner.instance-id", () -> INSTANCE_ID);
      registry.add("spring.cloud.gcp.spanner.database", () -> DATABASE_NAME);
      registry.add("spring.cloud.gcp.spanner.emulator.enabled", () -> "true");
      registry.add("spring.cloud.gcp.spanner.emulator-host", emulator::getEmulatorGrpcEndpoint);
    };
  }

  @Bean
  public Spanner testSpanner(SpannerEmulatorContainer emulator) {
    return SpannerOptions.newBuilder()
        .setProjectId(PROJECT_ID)
        .setEmulatorHost(emulator.getEmulatorGrpcEndpoint())
        .setCredentials(NoCredentials.getInstance())
        .build()
        .getService();
  }

  @Bean
  public DatabaseClient databaseClient(Spanner spanner) {
    return spanner.getDatabaseClient(DatabaseId.of(PROJECT_ID, INSTANCE_ID, DATABASE_NAME));
  }

  @Bean
  public DatabaseAdminClient databaseAdminClient(Spanner spanner) {
    return spanner.getDatabaseAdminClient();
  }

  @Bean
  public NoCredentialsProvider noCredentialsProvider() {
    return NoCredentialsProvider.create();
  }

  private void initializeInstanceAndDatabase(SpannerEmulatorContainer emulator) {
    try (Spanner spanner =
        SpannerOptions.newBuilder()
            .setProjectId(PROJECT_ID)
            .setEmulatorHost(emulator.getEmulatorGrpcEndpoint())
            .setCredentials(NoCredentials.getInstance())
            .build()
            .getService()) {

      InstanceAdminClient instanceAdmin = spanner.getInstanceAdminClient();
      instanceAdmin
          .createInstance(
              InstanceInfo.newBuilder(InstanceId.of(PROJECT_ID, INSTANCE_ID))
                  .setDisplayName("Test Instance")
                  .setInstanceConfigId(InstanceConfigId.of(PROJECT_ID, "emulator-config"))
                  .setNodeCount(1)
                  .build())
          .get();

      spanner
          .getDatabaseAdminClient()
          .createDatabase(
              INSTANCE_ID,
              DATABASE_NAME,
              List.of(
                  "CREATE TABLE smoke_test (id STRING(36) NOT NULL, value STRING(255)) PRIMARY"
                      + " KEY (id)",
                  "CREATE TABLE inventory_slots (slot_id STRING(36) NOT NULL,"
                      + " product_variant_id STRING(36) NOT NULL, slot_date STRING(10) NOT NULL,"
                      + " start_time STRING(5), end_time STRING(5),"
                      + " total_capacity INT64 NOT NULL, reserved_count INT64 NOT NULL,"
                      + " created_at TIMESTAMP NOT NULL"
                      + " OPTIONS (allow_commit_timestamp=true))"
                      + " PRIMARY KEY (slot_id)",
                  "CREATE TABLE inventory_holds (hold_id STRING(36) NOT NULL,"
                      + " slot_id STRING(36) NOT NULL, user_id STRING(36) NOT NULL,"
                      + " quantity INT64 NOT NULL, expires_at TIMESTAMP NOT NULL,"
                      + " created_at TIMESTAMP NOT NULL"
                      + " OPTIONS (allow_commit_timestamp=true))"
                      + " PRIMARY KEY (hold_id)",
                  "CREATE INDEX idx_holds_slot ON inventory_holds(slot_id)"))
          .get();
    } catch (ExecutionException | InterruptedException e) {
      throw new RuntimeException("Failed to initialize Spanner emulator", e);
    }
  }
}
