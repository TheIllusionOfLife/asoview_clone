package com.asoviewclone.ticketing;

import com.google.cloud.spring.autoconfigure.firestore.GcpFirestoreAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.GcpSpannerAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.SpannerRepositoriesAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.SpannerTransactionManagerAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    // Include java-common so the shared GlobalExceptionHandler picks up
    // validation exceptions uniformly across services. Without this,
    // ConstraintViolationException and friends fall through to
    // DefaultHandlerExceptionResolver and trip the ERROR re-dispatch.
    scanBasePackages = {"com.asoviewclone.ticketing", "com.asoviewclone.common"},
    exclude = {
      GcpSpannerAutoConfiguration.class,
      SpannerRepositoriesAutoConfiguration.class,
      SpannerTransactionManagerAutoConfiguration.class,
      GcpFirestoreAutoConfiguration.class
    })
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
