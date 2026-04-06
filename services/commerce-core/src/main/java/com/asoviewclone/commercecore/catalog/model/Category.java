package com.asoviewclone.commercecore.catalog.model;

import com.asoviewclone.common.audit.AuditFields;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "categories")
public class Category {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String slug;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(name = "display_order")
  private int displayOrder;

  @Column(name = "image_url")
  private String imageUrl;

  @Embedded private AuditFields audit = new AuditFields();

  protected Category() {}

  public Category(String name, String slug, UUID parentId, int displayOrder, String imageUrl) {
    this.name = name;
    this.slug = slug;
    this.parentId = parentId;
    this.displayOrder = displayOrder;
    this.imageUrl = imageUrl;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public UUID getParentId() {
    return parentId;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public AuditFields getAudit() {
    return audit;
  }
}
