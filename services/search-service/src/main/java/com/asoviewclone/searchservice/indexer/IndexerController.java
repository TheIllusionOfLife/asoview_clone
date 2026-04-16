package com.asoviewclone.searchservice.indexer;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/search/admin")
public class IndexerController {

  private final IndexerPort indexerService;

  public IndexerController(IndexerPort indexerService) {
    this.indexerService = indexerService;
  }

  @PostMapping("/reindex/{productId}")
  public void reindex(@PathVariable String productId) {
    indexerService.reindex(productId);
  }
}
