package com.asoviewclone.commercecore.config;

import com.google.cloud.spanner.DatabaseAdminClient;
import com.google.cloud.spanner.SpannerException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class SpannerDdlBootstrap implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(SpannerDdlBootstrap.class);

  private final DatabaseAdminClient adminClient;

  @Value("${spring.cloud.gcp.spanner.instance-id}")
  private String instanceId;

  @Value("${spring.cloud.gcp.spanner.database}")
  private String databaseName;

  public SpannerDdlBootstrap(DatabaseAdminClient adminClient) {
    this.adminClient = adminClient;
  }

  @Override
  public void run(String... args) throws Exception {
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources;
    try {
      resources = resolver.getResources("classpath:db/spanner/*.sql");
    } catch (IOException e) {
      log.info("No Spanner DDL files found, skipping bootstrap");
      return;
    }

    if (resources.length == 0) {
      log.info("No Spanner DDL files found, skipping bootstrap");
      return;
    }

    Arrays.sort(resources, (a, b) -> a.getFilename().compareTo(b.getFilename()));

    for (Resource resource : resources) {
      String sql = resource.getContentAsString(StandardCharsets.UTF_8);
      List<String> statements =
          Arrays.stream(sql.split(";"))
              .map(String::trim)
              .filter(s -> !s.isEmpty() && !s.startsWith("--"))
              .toList();

      if (statements.isEmpty()) {
        continue;
      }

      log.info(
          "Applying Spanner DDL from {}: {} statements", resource.getFilename(), statements.size());
      try {
        adminClient.updateDatabaseDdl(instanceId, databaseName, statements, null).get();
      } catch (ExecutionException e) {
        if (isAlreadyExists(e)) {
          log.info("DDL already applied: {}", resource.getFilename());
        } else {
          throw e;
        }
      }
    }
  }

  private boolean isAlreadyExists(ExecutionException e) {
    Throwable cause = e.getCause();
    if (cause instanceof SpannerException spannerEx) {
      return spannerEx.getErrorCode() == com.google.cloud.spanner.ErrorCode.FAILED_PRECONDITION;
    }
    if (cause != null && cause.getMessage() != null) {
      String msg = cause.getMessage();
      return msg.contains("Duplicate") || msg.contains("already exists");
    }
    return false;
  }
}
