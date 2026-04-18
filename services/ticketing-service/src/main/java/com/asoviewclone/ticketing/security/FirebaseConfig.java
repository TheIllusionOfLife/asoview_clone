package com.asoviewclone.ticketing.security;

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
import org.springframework.core.env.Environment;

@Configuration
public class FirebaseConfig {

  private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

  // Fall back through spring.cloud.gcp.spanner.project-id so operators only
  // need to set one env var (SPANNER_PROJECT_ID). Without the cascade the
  // Firebase SDK keeps its "asoview-clone" default while Spanner runs against
  // "asoview-clone-dev", and token verification fails with an aud-mismatch.
  @Value("${firebase.project-id:${spring.cloud.gcp.spanner.project-id:asoview-clone}}")
  private String projectId;

  /**
   * Initialize Firebase Admin. In production (any profile that is not {@code test} or {@code
   * local}) we fail fast if application-default credentials are missing, because silently falling
   * back to empty credentials makes every token verification succeed as "invalid" — a fail-open
   * posture for a security boundary. In {@code test}/{@code local} profiles we keep the empty
   * credentials fallback so Testcontainers / local bring-up does not require GCP creds.
   */
  @Bean
  public FirebaseApp firebaseApp(Environment env) {
    if (!FirebaseApp.getApps().isEmpty()) {
      return FirebaseApp.getInstance();
    }
    FirebaseOptions.Builder builder = FirebaseOptions.builder().setProjectId(projectId);
    try {
      builder.setCredentials(GoogleCredentials.getApplicationDefault());
    } catch (IOException e) {
      boolean allowEmpty = isTestOrLocalProfile(env);
      if (!allowEmpty) {
        throw new IllegalStateException(
            "No application default credentials available for Firebase Admin. "
                + "Refusing to initialize with empty credentials in production profile.",
            e);
      }
      log.warn(
          "No application default credentials (profile {}); using empty credentials for local/test.",
          String.join(",", env.getActiveProfiles()));
      builder.setCredentials(GoogleCredentials.newBuilder().build());
    }
    return FirebaseApp.initializeApp(builder.build());
  }

  @Bean
  public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
    return FirebaseAuth.getInstance(firebaseApp);
  }

  private static boolean isTestOrLocalProfile(Environment env) {
    for (String p : env.getActiveProfiles()) {
      if ("test".equals(p) || "local".equals(p)) {
        return true;
      }
    }
    return false;
  }
}
