package com.asoviewclone.commercecore.catalog.model;

import com.asoviewclone.common.audit.AuditFields;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "venue_id")
  private UUID venueId;

  @Column(name = "category_id")
  private UUID categoryId;

  @Column(nullable = false)
  private String title;

  private String description;

  @Column(name = "image_url")
  private String imageUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ProductStatus status = ProductStatus.DRAFT;

  @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  @org.hibernate.annotations.BatchSize(size = 20)
  private List<ProductVariant> variants = new ArrayList<>();

  @Embedded private AuditFields audit = new AuditFields();

  protected Product() {}

  public Product(
      UUID tenantId,
      UUID venueId,
      UUID categoryId,
      String title,
      String description,
      String imageUrl,
      ProductStatus status) {
    this.tenantId = tenantId;
    this.venueId = venueId;
    this.categoryId = categoryId;
    this.title = title;
    this.description = description;
    this.imageUrl = imageUrl;
    this.status = status;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getVenueId() {
    return venueId;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public ProductStatus getStatus() {
    return status;
  }

  public List<ProductVariant> getVariants() {
    return variants;
  }

  public AuditFields getAudit() {
    return audit;
  }
}
