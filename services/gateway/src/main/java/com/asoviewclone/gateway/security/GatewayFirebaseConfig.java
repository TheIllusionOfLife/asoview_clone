package com.asoviewclone.gateway.security;

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
public class GatewayFirebaseConfig {

  private static final Logger log = LoggerFactory.getLogger(GatewayFirebaseConfig.class);

  @Value("${firebase.project-id:asoview-clone}")
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
      log.warn("No application default credentials, using empty credentials: {}", e.getMessage());
      builder.setCredentials(GoogleCredentials.newBuilder().build());
    }
    return FirebaseApp.initializeApp(builder.build());
  }

  @Bean
  public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
    return FirebaseAuth.getInstance(firebaseApp);
  }
}
