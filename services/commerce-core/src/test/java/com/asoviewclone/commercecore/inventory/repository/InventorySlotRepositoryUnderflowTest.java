package com.asoviewclone.commercecore.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import com.asoviewclone.common.error.ConflictException;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Direct test for {@link InventorySlotRepository#releaseConfirmedHold} underflow guard. Verifies
 * that releasing more capacity than is currently reserved throws {@link ConflictException} and
 * leaves {@code reserved_count} unchanged (rather than silently clamping to zero).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class InventorySlotRepositoryUnderflowTest {

  @Autowired private InventorySlotRepository inventorySlotRepository;
  @Autowired private DatabaseClient databaseClient;

  @Test
  void releaseConfirmedHoldThrowsAndLeavesReservedCountUnchangedOnUnderflow() {
    String slotId = UUID.randomUUID().toString();
    String variantId = UUID.randomUUID().toString();

    databaseClient.write(
        List.of(
            Mutation.newInsertBuilder("inventory_slots")
                .set("slot_id")
                .to(slotId)
                .set("product_variant_id")
                .to(variantId)
                .set("slot_date")
                .to("2026-07-01")
                .set("start_time")
                .to("09:00")
                .set("end_time")
                .to("10:00")
                .set("total_capacity")
                .to(10)
                .set("reserved_count")
                .to(2)
                .set("created_at")
                .to(Timestamp.now())
                .build()));

    // The ConflictException is thrown inside the Spanner read-write transaction
    // callback, which Spanner wraps as a SpannerException. We assert on the
    // wrapped message and unwrap to verify the original ConflictException.
    assertThatThrownBy(() -> inventorySlotRepository.releaseConfirmedHold(slotId, 5))
        .satisfiesAnyOf(
            ex ->
                assertThat(ex)
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Cannot release 5")
                    .hasMessageContaining("reserved_count=2"),
            ex -> {
              assertThat(ex.getMessage()).contains("Cannot release 5").contains("reserved_count=2");
              Throwable root = ex;
              while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
              }
              assertThat(root).isInstanceOf(ConflictException.class);
            });

    assertThat(readReserved(slotId)).isEqualTo(2L);
  }

  private long readReserved(String slotId) {
    Statement stmt =
        Statement.newBuilder("SELECT reserved_count FROM inventory_slots WHERE slot_id = @s")
            .bind("s")
            .to(slotId)
            .build();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      if (rs.next()) {
        return rs.getLong("reserved_count");
      }
    }
    return -1;
  }
}
