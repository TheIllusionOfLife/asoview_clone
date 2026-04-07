package com.asoviewclone.commercecore.points.repository;

import com.asoviewclone.commercecore.points.model.PointLedgerEntry;
import com.asoviewclone.commercecore.points.model.PointReason;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointLedgerRepository extends JpaRepository<PointLedgerEntry, UUID> {

  boolean existsByReasonAndOrderId(PointReason reason, String orderId);
}
