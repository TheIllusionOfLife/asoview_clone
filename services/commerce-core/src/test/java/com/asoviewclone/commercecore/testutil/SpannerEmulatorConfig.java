package com.asoviewclone.commercecore.testutil;

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
import org.springframework.context.annotation.Primary;
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
            DockerImageName.parse("gcr.io/cloud-spanner-emulator/emulator:latest"));
    container.start();
    return container;
  }

  @Bean
  @Primary
  public Spanner testSpanner(SpannerEmulatorContainer emulator) throws Exception {
    String emulatorHost = emulator.getEmulatorGrpcEndpoint();

    Spanner spanner =
        SpannerOptions.newBuilder()
            .setProjectId(PROJECT_ID)
            .setEmulatorHost(emulatorHost)
            .setCredentials(NoCredentials.getInstance())
            .build()
            .getService();

    createInstanceAndDatabase(spanner);
    return spanner;
  }

  @Bean
  @Primary
  public DatabaseClient testDatabaseClient(Spanner spanner) {
    return spanner.getDatabaseClient(DatabaseId.of(PROJECT_ID, INSTANCE_ID, DATABASE_NAME));
  }

  @Bean
  @Primary
  public DatabaseAdminClient testDatabaseAdminClient(Spanner spanner) {
    return spanner.getDatabaseAdminClient();
  }

  private void createInstanceAndDatabase(Spanner spanner)
      throws InterruptedException, ExecutionException {
    InstanceAdminClient instanceAdmin = spanner.getInstanceAdminClient();
    instanceAdmin
        .createInstance(
            InstanceInfo.newBuilder(InstanceId.of(PROJECT_ID, INSTANCE_ID))
                .setDisplayName("Test Instance")
                .setInstanceConfigId(InstanceConfigId.of(PROJECT_ID, "emulator-config"))
                .setNodeCount(1)
                .build())
        .get();

    DatabaseAdminClient dbAdmin = spanner.getDatabaseAdminClient();
    dbAdmin
        .createDatabase(
            INSTANCE_ID,
            DATABASE_NAME,
            List.of(
                "CREATE TABLE smoke_test (id STRING(36) NOT NULL, value STRING(255)) PRIMARY KEY"
                    + " (id)"))
        .get();
  }
}
