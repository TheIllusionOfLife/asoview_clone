package com.asoviewclone.commercecore.payments.model;

import com.asoviewclone.common.audit.AuditFields;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "payments")
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID paymentId;

  @Column(name = "order_id", nullable = false)
  private String orderId;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false)
  private String currency = "JPY";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentStatus status = PaymentStatus.CREATED;

  private String provider;

  @Column(name = "provider_payment_id")
  private String providerPaymentId;

  // Persisted so the idempotent createPaymentIntent replay path can return the same client_secret
  // to the browser without re-fetching it from the provider. Nullable for gateways that don't
  // expose one (older Stub rows that pre-date V8 will land NULL).
  @Column(name = "client_secret")
  private String clientSecret;

  // Provider-hosted redirect / checkout URL the browser navigates to. Populated for
  // gateways like PayPay that complete payment off-site; null for in-page gateways
  // (Stripe Elements). Added in V15.
  @Column(name = "redirect_url")
  private String redirectUrl;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Embedded private AuditFields audit = new AuditFields();

  protected Payment() {}

  public Payment(
      String orderId, String userId, BigDecimal amount, String currency, String idempotencyKey) {
    this.orderId = orderId;
    this.userId = userId;
    this.amount = amount;
    this.currency = currency;
    this.idempotencyKey = idempotencyKey;
  }

  public UUID getPaymentId() {
    return paymentId;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getUserId() {
    return userId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getProviderPaymentId() {
    return providerPaymentId;
  }

  public void setProviderPaymentId(String providerPaymentId) {
    this.providerPaymentId = providerPaymentId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getRedirectUrl() {
    return redirectUrl;
  }

  public void setRedirectUrl(String redirectUrl) {
    this.redirectUrl = redirectUrl;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public AuditFields getAudit() {
    return audit;
  }
}
