package com.asoviewclone.commercecore;

import com.google.cloud.spring.autoconfigure.firestore.GcpFirestoreAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.GcpSpannerAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.SpannerRepositoriesAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.SpannerTransactionManagerAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

// Exclude GCP auto-configs that fail without credentials or are incompatible:
// - Spanner: Spring Cloud GCP 5.10.0 / Boot 3.4.4 TransactionManagerCustomizers issue
// - Firestore: pulled transitively by spring-cloud-gcp-starter-data-spanner, NPE without
// credentials
@SpringBootApplication(
    scanBasePackages = {"com.asoviewclone.commercecore", "com.asoviewclone.common"},
    exclude = {
      GcpSpannerAutoConfiguration.class,
      SpannerRepositoriesAutoConfiguration.class,
      SpannerTransactionManagerAutoConfiguration.class,
      GcpFirestoreAutoConfiguration.class
    })
@EnableRetry
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
