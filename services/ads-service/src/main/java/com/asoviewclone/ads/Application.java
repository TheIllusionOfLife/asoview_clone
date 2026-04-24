package com.asoviewclone.ads;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
  // Deliberately does NOT scan com.asoviewclone.common. The shared
  // GlobalExceptionHandler and the JSON security handlers live there, but
  // the security handlers implement spring-security-web interfaces and
  // ads-service doesn't pull spring-security. Pulling common in would
  // crash context loading on the missing AccessDeniedHandler class. If
  // ads-service ever grows a SecurityConfig, add spring-boot-starter-
  // security + scanBasePackages = {"com.asoviewclone.ads", "com.asoviewclone.common"}
  // in the same commit.

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
