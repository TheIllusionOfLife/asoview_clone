package com.asoviewclone.commercecore.catalog.service;

import com.asoviewclone.commercecore.catalog.model.Category;
import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CatalogService {

  List<Category> listCategories();

  Product getProduct(UUID productId);

  Page<Product> listProducts(UUID categoryId, ProductStatus status, Pageable pageable);
}
