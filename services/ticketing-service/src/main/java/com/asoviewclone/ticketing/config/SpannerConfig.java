package com.asoviewclone.ticketing.config;

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

  /**
   * Fine-grained access control role (see {@code db/spanner/V10__spanner_fine_grained_roles.sql}).
   * When set, every read/write from this service runs under the named Spanner role so IAM denies
   * anything the role isn't explicitly granted — e.g. DELETE on {@code scan_audit_log} even if the
   * workload's GSA holds {@code roles/spanner.databaseUser}. Leave empty for local emulator dev;
   * the emulator doesn't implement FGAC.
   */
  @Value("${spanner.database-role:}")
  private String databaseRole;

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
    // FGAC: the role is set on SpannerOptions (not per-DatabaseClient call) in the v6.x
    // google-cloud-spanner client. When non-blank, every subsequent read/write from this
    // Spanner instance runs under the named role. Left blank against the emulator, which
    // does not implement CREATE ROLE / GRANT.
    if (databaseRole != null && !databaseRole.isBlank()) {
      builder.setDatabaseRole(databaseRole);
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
