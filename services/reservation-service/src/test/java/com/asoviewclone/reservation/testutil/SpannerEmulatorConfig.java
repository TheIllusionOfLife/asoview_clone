package com.asoviewclone.reservation.testutil;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.NoCredentials;
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
                  "CREATE TABLE reservation_slots ("
                      + "slot_id STRING(36) NOT NULL,"
                      + "tenant_id STRING(36) NOT NULL,"
                      + "venue_id STRING(36) NOT NULL,"
                      + "product_id STRING(36) NOT NULL,"
                      + "slot_date STRING(10) NOT NULL,"
                      + "start_time STRING(5) NOT NULL,"
                      + "end_time STRING(5) NOT NULL,"
                      + "capacity INT64 NOT NULL,"
                      + "approved_count INT64 NOT NULL,"
                      + "waitlist_count INT64 NOT NULL,"
                      + "created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),"
                      + "updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)"
                      + ") PRIMARY KEY (slot_id)",
                  "CREATE INDEX idx_reservation_slots_venue_date"
                      + " ON reservation_slots(venue_id, slot_date)",
                  "CREATE TABLE reservations ("
                      + "reservation_id STRING(36) NOT NULL,"
                      + "tenant_id STRING(36) NOT NULL,"
                      + "venue_id STRING(36) NOT NULL,"
                      + "slot_id STRING(36) NOT NULL,"
                      + "consumer_user_id STRING(36) NOT NULL,"
                      + "status STRING(32) NOT NULL,"
                      + "idempotency_key STRING(64) NOT NULL,"
                      + "guest_name STRING(255) NOT NULL,"
                      + "guest_email STRING(255) NOT NULL,"
                      + "guest_count INT64 NOT NULL,"
                      + "reject_reason STRING(1024),"
                      + "cancel_reason STRING(1024),"
                      + "created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),"
                      + "updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)"
                      + ") PRIMARY KEY (reservation_id)",
                  "CREATE UNIQUE INDEX idx_reservations_idempotency"
                      + " ON reservations(idempotency_key)",
                  "CREATE INDEX idx_reservations_venue_status"
                      + " ON reservations(venue_id, status, created_at)",
                  "CREATE INDEX idx_reservations_consumer"
                      + " ON reservations(consumer_user_id, created_at)"))
          .get();
    } catch (ExecutionException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to initialize Spanner emulator", e);
    }
  }
}
