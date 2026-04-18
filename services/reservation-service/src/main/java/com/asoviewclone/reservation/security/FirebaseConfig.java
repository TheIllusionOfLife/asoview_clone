package com.asoviewclone.reservation.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirebaseConfig {

  private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

  // Fall back through spring.cloud.gcp.spanner.project-id so operators only
  // need to set one env var (SPANNER_PROJECT_ID). Without the cascade the
  // Firebase SDK keeps its "asoview-clone" default while Spanner runs against
  // "asoview-clone-dev", and token verification fails with an aud-mismatch.
  @Value("${firebase.project-id:${spring.cloud.gcp.spanner.project-id:asoview-clone}}")
  private String projectId;

  @Bean
  public FirebaseApp firebaseApp() {
    if (!FirebaseApp.getApps().isEmpty()) {
      return FirebaseApp.getInstance();
    }

    FirebaseOptions.Builder builder = FirebaseOptions.builder().setProjectId(projectId);

    try {
      builder.setCredentials(GoogleCredentials.getApplicationDefault());
    } catch (IOException e) {
      log.warn(
          "No application default credentials found, using empty credentials: {}", e.getMessage());
      builder.setCredentials(GoogleCredentials.newBuilder().build());
    }

    return FirebaseApp.initializeApp(builder.build());
  }

  @Bean
  public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
    return FirebaseAuth.getInstance(firebaseApp);
  }
}
