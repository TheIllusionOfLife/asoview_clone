package com.asoviewclone.commercecore.catalog.service;

import com.asoviewclone.commercecore.catalog.model.Category;
import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.repository.CategoryRepository;
import com.asoviewclone.commercecore.catalog.repository.ProductRepository;
import com.asoviewclone.common.error.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogServiceImpl implements CatalogService {

  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;

  public CatalogServiceImpl(
      CategoryRepository categoryRepository, ProductRepository productRepository) {
    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
  }

  @Override
  public List<Category> listCategories() {
    return categoryRepository.findAllByOrderByDisplayOrderAsc();
  }

  @Override
  public Product getProduct(UUID productId) {
    return productRepository
        .findByIdWithVariants(productId)
        .orElseThrow(() -> new NotFoundException("Product", productId.toString()));
  }

  @Override
  public Page<Product> listProducts(
      UUID categoryId, UUID venueId, ProductStatus status, Pageable pageable) {
    if (categoryId != null && venueId != null && status != null) {
      return productRepository.findByCategoryIdAndVenueIdAndStatus(
          categoryId, venueId, status, pageable);
    }
    if (categoryId != null && venueId != null) {
      // Both filters set but no status — do NOT silently drop the category
      // filter (the previous version fell through to venueId-only, which
      // broadened results and changed user-visible semantics).
      return productRepository.findByCategoryIdAndVenueId(categoryId, venueId, pageable);
    }
    if (venueId != null && status != null) {
      return productRepository.findByVenueIdAndStatus(venueId, status, pageable);
    }
    if (venueId != null) {
      return productRepository.findByVenueId(venueId, pageable);
    }
    if (categoryId != null && status != null) {
      return productRepository.findByCategoryIdAndStatus(categoryId, status, pageable);
    }
    if (categoryId != null) {
      return productRepository.findByCategoryId(categoryId, pageable);
    }
    if (status != null) {
      return productRepository.findByStatus(status, pageable);
    }
    return productRepository.findAll(pageable);
  }
}
