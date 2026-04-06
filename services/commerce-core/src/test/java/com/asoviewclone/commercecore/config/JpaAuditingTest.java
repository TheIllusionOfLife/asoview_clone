package com.asoviewclone.commercecore.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.commercecore.identity.model.Tenant;
import com.asoviewclone.commercecore.identity.repository.TenantRepository;
import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies Spring Data JPA auditing populates createdAt/updatedAt/createdBy/updatedBy on the
 * AuditFields embeddable from the application side.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class JpaAuditingTest {

  @Autowired private TenantRepository tenantRepository;

  @BeforeEach
  void setUp() {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            "test-user@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void persistAndUpdatePopulatesAuditFields() throws Exception {
    Tenant tenant =
        tenantRepository.save(new Tenant("Audit Tenant", "audit-" + UUID.randomUUID()));
    tenantRepository.flush();

    assertThat(tenant.getAudit().getCreatedAt()).isNotNull();
    assertThat(tenant.getAudit().getUpdatedAt()).isNotNull();
    assertThat(tenant.getAudit().getCreatedBy()).isEqualTo("test-user@example.com");
    assertThat(tenant.getAudit().getUpdatedBy()).isEqualTo("test-user@example.com");

    Instant firstUpdatedAt = tenant.getAudit().getUpdatedAt();

    // Switch the auditor to exercise LastModifiedBy.
    Thread.sleep(10);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "other-user@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

    // Touch a field via reflection to force Hibernate dirty detection and an UPDATE. Tenant
    // intentionally has no public setters.
    Tenant reloaded = tenantRepository.findById(tenant.getId()).orElseThrow();
    java.lang.reflect.Field nameField = Tenant.class.getDeclaredField("name");
    nameField.setAccessible(true);
    nameField.set(reloaded, "Audit Tenant Updated");
    tenantRepository.saveAndFlush(reloaded);

    Tenant afterUpdate = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(afterUpdate.getAudit().getUpdatedAt()).isAfterOrEqualTo(firstUpdatedAt);
    assertThat(afterUpdate.getAudit().getUpdatedBy()).isEqualTo("other-user@example.com");
    // createdBy must NOT change on update.
    assertThat(afterUpdate.getAudit().getCreatedBy()).isEqualTo("test-user@example.com");
  }
}
