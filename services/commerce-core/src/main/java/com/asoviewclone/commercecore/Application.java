package com.asoviewclone.commercecore;

import com.google.cloud.spring.autoconfigure.firestore.GcpFirestoreAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.GcpSpannerAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.SpannerRepositoriesAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.SpannerTransactionManagerAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

// Exclude GCP auto-configs that fail without credentials or are incompatible.
// Re-evaluated under Spring Boot 4.0.5 + Spring Cloud GCP 8.0.1: removing the
// exclusions still surfaces the same TransactionManagerCustomizers wiring
// failure for Spanner and the Firestore-without-credentials NPE, so the
// exclusions remain necessary.
@SpringBootApplication(
    scanBasePackages = {"com.asoviewclone.commercecore", "com.asoviewclone.common"},
    exclude = {
      GcpSpannerAutoConfiguration.class,
      SpannerRepositoriesAutoConfiguration.class,
      SpannerTransactionManagerAutoConfiguration.class,
      GcpFirestoreAutoConfiguration.class
    })
@EnableRetry
@EnableScheduling
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
