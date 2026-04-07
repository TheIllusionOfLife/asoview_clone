package com.asoviewclone.searchservice.query.dto;

import com.asoviewclone.searchservice.query.model.SearchHit;
import java.util.List;

/**
 * Page-shaped envelope mirroring Spring Data's {@code Page} JSON so the frontend type can be
 * shared with commerce-core's {@code /v1/products} response.
 */
public record ProductSearchResponse(
    List<SearchHit> content, long totalElements, int number, int size) {}
