package com.asoviewclone.searchservice.query.model;

public record SearchHit(
    String productId,
    String name,
    String description,
    Long minPrice,
    String areaId,
    String categoryId) {}
