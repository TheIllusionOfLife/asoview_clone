package com.asoviewclone.commercecore.catalog.model;

import com.asoviewclone.common.audit.AuditFields;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "product_variants")
public class ProductVariant {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(nullable = false)
  private String name;

  @Column(name = "price_amount", nullable = false)
  private BigDecimal priceAmount;

  @Column(name = "price_currency", nullable = false)
  private String priceCurrency = "JPY";

  @Column(name = "duration_minutes")
  private Integer durationMinutes;

  @Column(name = "max_participants")
  private Integer maxParticipants;

  @Embedded private AuditFields audit = new AuditFields();

  protected ProductVariant() {}

  public ProductVariant(
      Product product,
      String name,
      BigDecimal priceAmount,
      String priceCurrency,
      Integer durationMinutes,
      Integer maxParticipants) {
    this.product = product;
    this.name = name;
    this.priceAmount = priceAmount;
    this.priceCurrency = priceCurrency;
    this.durationMinutes = durationMinutes;
    this.maxParticipants = maxParticipants;
  }

  public UUID getId() {
    return id;
  }

  public Product getProduct() {
    return product;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getPriceAmount() {
    return priceAmount;
  }

  public String getPriceCurrency() {
    return priceCurrency;
  }

  public Integer getDurationMinutes() {
    return durationMinutes;
  }

  public Integer getMaxParticipants() {
    return maxParticipants;
  }

  public AuditFields getAudit() {
    return audit;
  }
}
