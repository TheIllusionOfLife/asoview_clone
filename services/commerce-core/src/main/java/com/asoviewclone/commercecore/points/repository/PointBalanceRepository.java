package com.asoviewclone.commercecore.points.repository;

import com.asoviewclone.commercecore.points.model.PointBalance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointBalanceRepository extends JpaRepository<PointBalance, UUID> {}
