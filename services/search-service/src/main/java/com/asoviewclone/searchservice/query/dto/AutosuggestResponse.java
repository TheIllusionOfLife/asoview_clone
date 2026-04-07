package com.asoviewclone.searchservice.query.dto;

import java.util.List;

public record AutosuggestResponse(List<Suggestion> suggestions) {

  public record Suggestion(String productId, String name) {}
}
