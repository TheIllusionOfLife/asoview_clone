package com.asoviewclone.commercecore.catalog.controller;

import com.asoviewclone.commercecore.catalog.controller.dto.CategoryResponse;
import com.asoviewclone.commercecore.catalog.service.CatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/categories")
public class CategoryController {

  private final CatalogService catalogService;

  public CategoryController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping
  public List<CategoryResponse> listCategories() {
    return catalogService.listCategories().stream().map(CategoryResponse::from).toList();
  }
}
