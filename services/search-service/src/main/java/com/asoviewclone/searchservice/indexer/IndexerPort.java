package com.asoviewclone.searchservice.indexer;

/**
 * Indexer surface backed by Vertex AI Search (Discovery Engine API).
 *
 * <p>Backfill completion is modelled as "complete" (not "started"): the implementation must only
 * call {@link #markBackfillComplete()} when the entire backfill finished without per-document
 * failures and without unpaginated pages. This prevents a partial run from blocking future retries
 * via a stale marker.
 */
public interface IndexerPort {

  void reindex(String productId);

  /** Update the popularity score for an existing document. Returns true on success. */
  boolean updatePopularityScore(String productId, long score);

  boolean isBackfillComplete();

  void markBackfillComplete();
}
