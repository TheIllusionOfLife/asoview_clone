package com.asoviewclone.commercecore.identity.repository;

import com.asoviewclone.commercecore.identity.model.TenantUser;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {

  List<TenantUser> findByUserId(UUID userId);

  List<TenantUser> findByTenantId(UUID tenantId);
}
