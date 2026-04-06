package com.asoviewclone.commercecore.catalog.controller;

import com.asoviewclone.commercecore.catalog.controller.dto.ProductResponse;
import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.service.CatalogService;
import com.asoviewclone.commercecore.inventory.service.InventoryQueryService;
import com.asoviewclone.commercecore.inventory.service.InventoryQueryService.AvailabilityEntry;
import java.util.List;
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
  private final InventoryQueryService inventoryQueryService;

  public ProductController(
      CatalogService catalogService, InventoryQueryService inventoryQueryService) {
    this.catalogService = catalogService;
    this.inventoryQueryService = inventoryQueryService;
  }

  @GetMapping
  public Page<ProductResponse> listProducts(
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) UUID area,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    // Public endpoint always shows ACTIVE only. Client cannot override status.
    // `area` is an alias for venueId in phase 2 — see AreaController for the rationale.
    return catalogService
        .listProducts(categoryId, area, ProductStatus.ACTIVE, PageRequest.of(page, size))
        .map(ProductResponse::from);
  }

  @GetMapping("/{productId}")
  public ProductResponse getProduct(@PathVariable UUID productId) {
    Product product = catalogService.getProduct(productId);
    if (product.getStatus() != ProductStatus.ACTIVE) {
      throw new com.asoviewclone.common.error.NotFoundException("Product", productId.toString());
    }
    return ProductResponse.from(product);
  }

  @GetMapping("/{productId}/availability")
  public List<AvailabilityEntry> getAvailability(
      @PathVariable UUID productId, @RequestParam String from, @RequestParam String to) {
    // Gate on ACTIVE status so callers cannot enumerate inventory for
    // draft/hidden products via a known product id.
    Product product = catalogService.getProduct(productId);
    if (product.getStatus() != ProductStatus.ACTIVE) {
      throw new com.asoviewclone.common.error.NotFoundException("Product", productId.toString());
    }
    return inventoryQueryService.getProductAvailability(productId, from, to);
  }
}
