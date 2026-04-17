package com.asoviewclone.searchservice.query.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Jackson DTO mirroring the Vertex AI Search (Discovery Engine) schema in {@code
 * vertex/products-schema.json}. Field names must match the schema exactly — they map 1:1 to {@code
 * Document.struct_data} entries.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductDoc(
    String productId,
    String name,
    String description,
    String areaId,
    String categoryId,
    Long minPrice,
    String status,
    String indexedAt,
    Long popularityScore) {}
