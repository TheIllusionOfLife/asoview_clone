package com.asoviewclone.commercecore.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Enables Spring Data JPA auditing so that {@code @CreatedDate}, {@code @LastModifiedDate},
 * {@code @CreatedBy}, and {@code @LastModifiedBy} on {@code AuditFields} are populated on
 * persist/update. The auditor is resolved from the Spring Security context, falling back to {@code
 * "system"} when no authenticated principal is present (scheduled jobs, tests).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditingConfig {

  @Bean
  public AuditorAware<String> auditorAware() {
    return () -> {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
        return Optional.of("system");
      }
      return Optional.of(auth.getName());
    };
  }
}
