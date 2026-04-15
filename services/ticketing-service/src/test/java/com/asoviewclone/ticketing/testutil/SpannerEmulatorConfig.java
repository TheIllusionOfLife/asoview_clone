package com.asoviewclone.ticketing.testutil;

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

/**
 * Testcontainers-backed Spanner emulator. Mirrors the commerce-core schema through V9 plus the
 * indices required by the redeem query. Must be kept in sync with {@code
 * services/commerce-core/src/main/resources/db/spanner/V*.sql}.
 */
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
                  // entitlements
                  "CREATE TABLE entitlements ("
                      + "entitlement_id STRING(36) NOT NULL,"
                      + " order_id STRING(36) NOT NULL,"
                      + " order_item_id STRING(36) NOT NULL,"
                      + " user_id STRING(36) NOT NULL,"
                      + " product_variant_id STRING(36) NOT NULL,"
                      + " type STRING(32) NOT NULL, status STRING(32) NOT NULL,"
                      + " valid_from TIMESTAMP, valid_until TIMESTAMP,"
                      + " created_at TIMESTAMP NOT NULL"
                      + " OPTIONS (allow_commit_timestamp=true))"
                      + " PRIMARY KEY (entitlement_id)",
                  // ticket_passes (with V6 columns; used_at allows commit timestamp per V6)
                  "CREATE TABLE ticket_passes ("
                      + "ticket_pass_id STRING(36) NOT NULL,"
                      + " entitlement_id STRING(36) NOT NULL,"
                      + " qr_code_payload STRING(255) NOT NULL,"
                      + " status STRING(32) NOT NULL,"
                      + " used_at TIMESTAMP OPTIONS (allow_commit_timestamp=true),"
                      + " venue_id STRING(36), tenant_id STRING(36),"
                      + " created_at TIMESTAMP NOT NULL"
                      + " OPTIONS (allow_commit_timestamp=true))"
                      + " PRIMARY KEY (ticket_pass_id)",
                  "CREATE INDEX idx_ticket_passes_qr ON ticket_passes(qr_code_payload)",
                  // V7 scan_audit_log
                  "CREATE TABLE scan_audit_log ("
                      + "scan_id STRING(36) NOT NULL,"
                      + " tenant_id STRING(36) NOT NULL,"
                      + " ticket_pass_id STRING(64),"
                      + " scanner_user_id STRING(64) NOT NULL,"
                      + " scanner_device_id STRING(64),"
                      + " venue_id STRING(36),"
                      + " outcome STRING(32) NOT NULL,"
                      + " source_ip STRING(64),"
                      + " idempotency_key STRING(64),"
                      + " scanned_at TIMESTAMP NOT NULL"
                      + " OPTIONS (allow_commit_timestamp=true))"
                      + " PRIMARY KEY (scan_id)",
                  "CREATE INDEX idx_scan_audit_pass ON"
                      + " scan_audit_log (ticket_pass_id, scanned_at DESC)",
                  "CREATE INDEX idx_scan_audit_outcome ON"
                      + " scan_audit_log (tenant_id, outcome, scanned_at DESC)",
                  // V8 ticket_redeem_idempotency
                  "CREATE TABLE ticket_redeem_idempotency ("
                      + "idempotency_key STRING(64) NOT NULL,"
                      + " scanner_user_id STRING(64) NOT NULL,"
                      + " ticket_pass_id STRING(64),"
                      + " outcome STRING(32) NOT NULL,"
                      + " response_body_hash STRING(64) NOT NULL,"
                      + " created_at TIMESTAMP NOT NULL"
                      + " OPTIONS (allow_commit_timestamp=true))"
                      + " PRIMARY KEY (idempotency_key)",
                  // V9 revoked_sessions
                  "CREATE TABLE revoked_sessions ("
                      + "user_id STRING(64) NOT NULL,"
                      + " session_id STRING(64) NOT NULL,"
                      + " revoked_at TIMESTAMP NOT NULL"
                      + " OPTIONS (allow_commit_timestamp=true))"
                      + " PRIMARY KEY (user_id, session_id)"))
          .get();
    } catch (ExecutionException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to initialize Spanner emulator", e);
    }
  }
}
