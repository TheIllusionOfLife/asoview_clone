package com.asoviewclone.commercecore.catalog.event;

/**
 * Domain event raised after a product row is upserted in commerce-core. Consumed by the search
 * indexer (currently log-only; will be wired to Pub/Sub in a follow-up PR).
 */
public record ProductUpsertedEvent(String productId) {}
