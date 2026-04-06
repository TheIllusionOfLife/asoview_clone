package com.asoviewclone.commercecore.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.commercecore.identity.model.User;
import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class UserRepositoryTest {

  @Autowired private UserRepository userRepository;

  @Test
  void saveAndFindByFirebaseUid() {
    User user = new User("firebase-123", "test@example.com", "Test User");
    userRepository.save(user);

    Optional<User> found = userRepository.findByFirebaseUid("firebase-123");
    assertThat(found).isPresent();
    assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    assertThat(found.get().getDisplayName()).isEqualTo("Test User");
    assertThat(found.get().getId()).isNotNull();
  }

  @Test
  void findByEmail() {
    User user = new User("firebase-456", "another@example.com", "Another User");
    userRepository.save(user);

    Optional<User> found = userRepository.findByEmail("another@example.com");
    assertThat(found).isPresent();
    assertThat(found.get().getFirebaseUid()).isEqualTo("firebase-456");
  }
}
