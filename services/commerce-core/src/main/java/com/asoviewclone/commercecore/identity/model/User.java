package com.asoviewclone.commercecore.identity.model;

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
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "firebase_uid", nullable = false, unique = true)
  private String firebaseUid;

  @Column(nullable = false)
  private String email;

  @Column(name = "display_name")
  private String displayName;

  @Embedded private AuditFields audit = new AuditFields();

  protected User() {}

  public User(String firebaseUid, String email, String displayName) {
    this.firebaseUid = firebaseUid;
    this.email = email;
    this.displayName = displayName;
  }

  public UUID getId() {
    return id;
  }

  public String getFirebaseUid() {
    return firebaseUid;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public AuditFields getAudit() {
    return audit;
  }
}
