package com.asoviewclone.commercecore.payments.repository;

import com.asoviewclone.commercecore.payments.model.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  Optional<Payment> findByIdempotencyKey(String idempotencyKey);

  Optional<Payment> findByOrderId(String orderId);
}
