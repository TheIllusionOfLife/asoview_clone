package com.asoviewclone.commercecore.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.commercecore.inventory.model.InventoryHold;
import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class InventoryConcurrencyTest {

  @Autowired private InventoryService inventoryService;
  @Autowired private DatabaseClient databaseClient;

  private String slotId;

  @BeforeEach
  void setUp() {
    slotId = UUID.randomUUID().toString();
    String variantId = UUID.randomUUID().toString();

    // Create a slot with capacity = 1
    databaseClient.write(
        List.of(
            Mutation.newInsertBuilder("inventory_slots")
                .set("slot_id")
                .to(slotId)
                .set("product_variant_id")
                .to(variantId)
                .set("slot_date")
                .to("2026-05-01")
                .set("total_capacity")
                .to(1)
                .set("reserved_count")
                .to(0)
                .set("created_at")
                .to(Timestamp.now())
                .build()));
  }

  @Test
  void concurrentHoldsOnLastSlotExactlyOneSucceeds() throws Exception {
    int threadCount = 5;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger(0);
    AtomicInteger failures = new AtomicInteger(0);

    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      String userId = UUID.randomUUID().toString();
      futures.add(
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  InventoryHold hold = inventoryService.holdInventory(slotId, userId, 1);
                  if (hold != null) {
                    successes.incrementAndGet();
                  }
                } catch (Exception e) {
                  failures.incrementAndGet();
                }
              }));
    }

    // Release all threads at once
    startLatch.countDown();

    for (Future<?> f : futures) {
      f.get();
    }
    executor.shutdown();

    assertThat(successes.get()).isEqualTo(1);
    assertThat(failures.get()).isEqualTo(threadCount - 1);
  }

  @Test
  void holdAndConfirmUpdatesReservedCount() {
    String userId = UUID.randomUUID().toString();
    InventoryHold hold = inventoryService.holdInventory(slotId, userId, 1);
    assertThat(hold).isNotNull();
    assertThat(hold.slotId()).isEqualTo(slotId);

    inventoryService.confirmHold(hold.holdId());

    // Verify reserved count incremented: trying another hold should fail
    try {
      inventoryService.holdInventory(slotId, UUID.randomUUID().toString(), 1);
      assertThat(true).as("Should have thrown ConflictException").isFalse();
    } catch (Exception e) {
      assertThat(e.getMessage()).contains("Insufficient capacity");
    }
  }

  @Test
  void holdAndReleaseRestoresCapacity() {
    String userId = UUID.randomUUID().toString();
    InventoryHold hold = inventoryService.holdInventory(slotId, userId, 1);
    assertThat(hold).isNotNull();

    inventoryService.releaseHold(hold.holdId());

    // After release, another hold should succeed
    InventoryHold newHold = inventoryService.holdInventory(slotId, UUID.randomUUID().toString(), 1);
    assertThat(newHold).isNotNull();
  }
}
