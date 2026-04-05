package com.asoviewclone.commercecore.catalog.controller;

import com.asoviewclone.commercecore.catalog.controller.dto.ProductResponse;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.service.CatalogService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/products")
public class ProductController {

  private final CatalogService catalogService;

  public ProductController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping
  public Page<ProductResponse> listProducts(
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) ProductStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    // Default to ACTIVE to prevent exposing DRAFT/ARCHIVED products publicly
    ProductStatus effectiveStatus = (status != null) ? status : ProductStatus.ACTIVE;
    return catalogService
        .listProducts(categoryId, effectiveStatus, PageRequest.of(page, size))
        .map(ProductResponse::from);
  }

  @GetMapping("/{productId}")
  public ProductResponse getProduct(@PathVariable UUID productId) {
    return ProductResponse.from(catalogService.getProduct(productId));
  }
}
