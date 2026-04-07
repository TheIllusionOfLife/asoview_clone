package com.asoviewclone.commercecore.points.discount;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDiscountRepository extends JpaRepository<OrderDiscount, UUID> {

  Optional<OrderDiscount> findByOrderId(String orderId);

  void deleteByOrderId(String orderId);
}
