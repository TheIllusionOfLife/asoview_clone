package com.asoviewclone.commercecore.identity.repository;

import com.asoviewclone.commercecore.identity.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByFirebaseUid(String firebaseUid);

  Optional<User> findByEmail(String email);
}
