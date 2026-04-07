package com.asoviewclone.commercecore.reviews.model;

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
import java.util.UUID;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "reviews")
public class Review {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(nullable = false)
  private short rating;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String body;

  @Column(nullable = false)
  private String language = "ja";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReviewStatus status = ReviewStatus.PUBLISHED;

  @Column(name = "helpful_count", nullable = false)
  private int helpfulCount = 0;

  @Embedded private AuditFields audit = new AuditFields();

  protected Review() {}

  public Review(UUID userId, UUID productId, short rating, String title, String body) {
    this.userId = userId;
    this.productId = productId;
    this.rating = rating;
    this.title = title;
    this.body = body;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getProductId() {
    return productId;
  }

  public short getRating() {
    return rating;
  }

  public void setRating(short rating) {
    this.rating = rating;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public ReviewStatus getStatus() {
    return status;
  }

  public void setStatus(ReviewStatus status) {
    this.status = status;
  }

  public int getHelpfulCount() {
    return helpfulCount;
  }

  public void setHelpfulCount(int helpfulCount) {
    this.helpfulCount = helpfulCount;
  }

  public AuditFields getAudit() {
    return audit;
  }
}
