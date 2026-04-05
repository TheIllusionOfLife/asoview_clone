package com.asoviewclone.commercecore;

import com.google.cloud.spring.autoconfigure.spanner.GcpSpannerAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.SpannerRepositoriesAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.SpannerTransactionManagerAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Exclude Spanner auto-config due to Spring Cloud GCP 5.10.0 / Spring Boot 3.4.4
// compatibility issues with TransactionManagerCustomizers and Supplier<DatabaseClient>.
// Spanner beans are wired manually in SpannerEmulatorConfig (test) and will be
// provided via a production SpannerConfig when deploying against real Spanner.
@SpringBootApplication(
    scanBasePackages = {"com.asoviewclone.commercecore", "com.asoviewclone.common"},
    exclude = {
      GcpSpannerAutoConfiguration.class,
      SpannerRepositoriesAutoConfiguration.class,
      SpannerTransactionManagerAutoConfiguration.class
    })
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
