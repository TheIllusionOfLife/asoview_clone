package com.asoviewclone.commercecore.identity.repository;

import com.asoviewclone.commercecore.identity.model.Venue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

  List<Venue> findByTenantId(UUID tenantId);
}
