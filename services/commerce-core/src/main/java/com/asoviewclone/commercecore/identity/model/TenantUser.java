package com.asoviewclone.commercecore.identity.model;

import com.asoviewclone.common.audit.AuditFields;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tenant_users")
public class TenantUser {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TenantRole role;

  @Embedded private AuditFields audit = new AuditFields();

  protected TenantUser() {}

  public TenantUser(UUID tenantId, UUID userId, TenantRole role) {
    this.tenantId = tenantId;
    this.userId = userId;
    this.role = role;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getUserId() {
    return userId;
  }

  public TenantRole getRole() {
    return role;
  }

  public AuditFields getAudit() {
    return audit;
  }
}
