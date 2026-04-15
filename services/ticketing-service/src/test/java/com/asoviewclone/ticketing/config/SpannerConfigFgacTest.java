package com.asoviewclone.ticketing.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Asserts the FGAC role plumbing sets {@code SpannerOptions.databaseRole} when configured, and
 * leaves it null otherwise. Doesn't exercise Spanner IAM — the Spanner emulator used by the rest of
 * the test suite doesn't implement CREATE ROLE / GRANT. Live-cluster enforcement is verified
 * manually (see PR body for the {@code gcloud spanner databases execute-sql} DELETE check).
 */
class SpannerConfigFgacTest {

  @SpringBootTest(classes = {SpannerConfig.class})
  @ActiveProfiles("test")
  @TestPropertySource(
      properties = {
        "spring.cloud.gcp.spanner.project-id=test",
        "spring.cloud.gcp.spanner.instance-id=test",
        "spring.cloud.gcp.spanner.database=test",
        "spring.cloud.gcp.spanner.emulator-host=localhost:0",
        "spanner.database-role=ticketing_service"
      })
  static class WithRole {
    @Autowired Spanner spanner;

    @Test
    void spanner_optionsCarryTheConfiguredRole() {
      SpannerOptions options = spanner.getOptions();
      assertThat(options.getDatabaseRole()).isEqualTo("ticketing_service");
    }
  }

  @SpringBootTest(classes = {SpannerConfig.class})
  @ActiveProfiles("test")
  @TestPropertySource(
      properties = {
        "spring.cloud.gcp.spanner.project-id=test",
        "spring.cloud.gcp.spanner.instance-id=test",
        "spring.cloud.gcp.spanner.database=test",
        "spring.cloud.gcp.spanner.emulator-host=localhost:0"
      })
  static class WithoutRole {
    @Autowired Spanner spanner;

    @Test
    void spanner_optionsLeaveRoleUnsetForEmulator() {
      SpannerOptions options = spanner.getOptions();
      assertThat(options.getDatabaseRole()).isNull();
    }
  }
}
