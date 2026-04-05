package com.asoviewclone.commercecore.testutil;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class StoresSmokeTest {

  @Autowired private DataSource dataSource;

  @Autowired private StringRedisTemplate redisTemplate;

  @Autowired private DatabaseClient spannerClient;

  @Test
  void postgresIsReachable() throws Exception {
    try (var conn = dataSource.getConnection();
        var stmt = conn.createStatement();
        var rs = stmt.executeQuery("SELECT 1")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1)).isEqualTo(1);
    }
  }

  @Test
  void redisIsReachable() {
    redisTemplate.opsForValue().set("smoke-test-key", "ok");
    String value = redisTemplate.opsForValue().get("smoke-test-key");
    assertThat(value).isEqualTo("ok");
    redisTemplate.delete("smoke-test-key");
  }

  @Test
  void spannerEmulatorIsReachable() {
    // Write and read via raw Spanner client
    spannerClient.write(
        List.of(
            Mutation.newInsertBuilder("smoke_test")
                .set("id")
                .to("1")
                .set("value")
                .to("hello")
                .build()));

    try (ResultSet rs =
        spannerClient
            .singleUse()
            .executeQuery(Statement.of("SELECT value FROM smoke_test WHERE id = '1'"))) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("value")).isEqualTo("hello");
    }
  }
}
