package com.asoviewclone.searchservice.query.controller;

import com.asoviewclone.searchservice.query.dto.AutosuggestResponse;
import com.asoviewclone.searchservice.query.dto.ProductSearchResponse;
import com.asoviewclone.searchservice.query.service.SearchQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/search")
public class SearchController {

  private final SearchQueryService searchQueryService;

  public SearchController(SearchQueryService searchQueryService) {
    this.searchQueryService = searchQueryService;
  }

  @GetMapping
  public ProductSearchResponse search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) Long minPrice,
      @RequestParam(required = false) Long maxPrice,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return searchQueryService.search(q, area, category, minPrice, maxPrice, sort, page, size);
  }

  @GetMapping("/suggest")
  public AutosuggestResponse suggest(@RequestParam("q") String q) {
    return searchQueryService.suggest(q);
  }
}
