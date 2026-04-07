package com.asoviewclone.searchservice.query.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Jackson DTO mirroring the OpenSearch index mapping in {@code
 * opensearch/products-index-template.json}.
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
    String indexedAt) {}
