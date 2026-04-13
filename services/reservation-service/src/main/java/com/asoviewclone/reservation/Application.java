package com.asoviewclone.reservation;

import com.google.cloud.spring.autoconfigure.firestore.GcpFirestoreAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.GcpSpannerAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.SpannerRepositoriesAutoConfiguration;
import com.google.cloud.spring.autoconfigure.spanner.SpannerTransactionManagerAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
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
