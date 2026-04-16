package com.asoviewclone.searchservice.indexer;

/**
 * Provider-neutral indexer surface. Implementations back this with OpenSearch (legacy) or Vertex AI
 * Search.
 *
 * <p>Backfill completion is modelled as "complete" (not "started"): a provider must only call
 * {@link #markBackfillComplete()} when the entire backfill finished without per-document failures
 * and without unpaginated pages. This prevents a partial run from blocking future retries via a
 * stale marker.
 */
public interface IndexerPort {

  void reindex(String productId);

  /** Update the popularity score for an existing document. Returns true on success. */
  boolean updatePopularityScore(String productId, long score);

  boolean isBackfillComplete();

  void markBackfillComplete();
}
