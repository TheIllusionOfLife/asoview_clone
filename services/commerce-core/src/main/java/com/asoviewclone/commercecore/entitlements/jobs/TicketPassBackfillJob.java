package com.asoviewclone.commercecore.entitlements.jobs;

import com.asoviewclone.commercecore.entitlements.service.VenueTenantResolver;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-shot job that populates {@code venue_id} / {@code tenant_id} on legacy pre-V6 ticket_passes
 * rows. Gated behind {@code commerce.ticket-pass-backfill.enabled=true} so it only wires into the
 * context when explicitly run. Restart-idempotent: queries by NULL column so reruns only touch
 * still-unpopulated rows.
 *
 * <p>Batches are sized by Spanner mutations, not by row count — each updated row emits 2 mutations
 * (venue_id + tenant_id), so the configurable {@code batch-mutations} ceiling (default 5000) caps
 * at ~2500 rows per transaction, well under Spanner's 80k mutation limit.
 *
 * <p>Unresolvable rows (variant UUID no longer resolves to a product with venue/tenant) are
 * surfaced in the {@link Report}. Ops wrapper should treat non-zero {@code unresolved} as a signal
 * to investigate before rerunning.
 */
@Component
@ConditionalOnProperty(name = "commerce.ticket-pass-backfill.enabled", havingValue = "true")
public class TicketPassBackfillJob {

  private static final Logger log = LoggerFactory.getLogger(TicketPassBackfillJob.class);
  private static final int MUTATIONS_PER_ROW = 2;

  private final DatabaseClient databaseClient;
  private final VenueTenantResolver resolver;
  private final int batchMutations;

  public TicketPassBackfillJob(
      DatabaseClient databaseClient,
      VenueTenantResolver resolver,
      @Value("${commerce.ticket-pass-backfill.batch-mutations:5000}") int batchMutations) {
    this.databaseClient = databaseClient;
    this.resolver = resolver;
    this.batchMutations = batchMutations;
  }

  public Report run() {
    int maxRowsPerBatch = Math.max(1, batchMutations / MUTATIONS_PER_ROW);
    int updated = 0;
    java.util.LinkedHashSet<String> unresolvedIds = new java.util.LinkedHashSet<>();

    while (true) {
      List<Candidate> candidates = readBatch(maxRowsPerBatch);
      if (candidates.isEmpty()) {
        break;
      }

      List<Mutation> mutations = new ArrayList<>();
      int newUnresolvedThisBatch = 0;
      for (Candidate c : candidates) {
        if (unresolvedIds.contains(c.passId)) {
          // Already reported on a previous batch; skip silently so the report counts each
          // unresolvable pass exactly once.
          continue;
        }
        Optional<VenueTenantResolver.Resolution> resolution = resolver.resolve(c.productVariantId);
        if (resolution.isEmpty()) {
          log.error(
              "TicketPassBackfillJob: unresolvable pass_id={} variant_id={}",
              c.passId,
              c.productVariantId);
          unresolvedIds.add(c.passId);
          newUnresolvedThisBatch++;
          continue;
        }
        mutations.add(
            Mutation.newUpdateBuilder("ticket_passes")
                .set("ticket_pass_id")
                .to(c.passId)
                .set("venue_id")
                .to(resolution.get().venueId())
                .set("tenant_id")
                .to(resolution.get().tenantId())
                .build());
      }

      if (!mutations.isEmpty()) {
        databaseClient.write(mutations);
        updated += mutations.size();
      }

      // Termination. Two independent reasons to stop:
      //   1. Short batch -> cursor exhausted, no rows left matching WHERE venue_id IS NULL.
      //   2. No progress -> every candidate in this full-sized batch was already in
      //      unresolvedIds (nothing written, nothing newly discovered). Without this guard,
      //      a population of >=maxRowsPerBatch unresolvable rows would spin forever: the
      //      NULL-filter query keeps re-serving them, the dedup set keeps skipping them,
      //      and candidates.size() stays at the limit.
      boolean madeProgress = !mutations.isEmpty() || newUnresolvedThisBatch > 0;
      if (candidates.size() < maxRowsPerBatch || !madeProgress) {
        break;
      }
    }

    Report r = new Report(updated, unresolvedIds.size(), new ArrayList<>(unresolvedIds));
    log.info(
        "TicketPassBackfillJob complete: updated={} unresolved={}", r.updated(), r.unresolved());
    return r;
  }

  private List<Candidate> readBatch(int limit) {
    List<Candidate> out = new ArrayList<>();
    try (ResultSet rs =
        databaseClient
            .singleUse()
            .executeQuery(
                Statement.newBuilder(
                        "SELECT tp.ticket_pass_id, e.product_variant_id "
                            + "FROM ticket_passes tp "
                            + "JOIN entitlements e ON e.entitlement_id = tp.entitlement_id "
                            + "WHERE tp.venue_id IS NULL OR tp.tenant_id IS NULL "
                            + "LIMIT @lim")
                    .bind("lim")
                    .to(limit)
                    .build())) {
      while (rs.next()) {
        out.add(new Candidate(rs.getString("ticket_pass_id"), rs.getString("product_variant_id")));
      }
    }
    return out;
  }

  private record Candidate(String passId, String productVariantId) {}

  /**
   * Result of a backfill run. {@code unresolvedPassIds} is deduped — each unresolvable pass is
   * listed once even if it spanned multiple batches. Ops should treat {@code unresolved > 0} as a
   * signal to investigate (orphaned entitlements, deleted variants) before rerunning.
   */
  public record Report(int updated, int unresolved, List<String> unresolvedPassIds) {}
}
