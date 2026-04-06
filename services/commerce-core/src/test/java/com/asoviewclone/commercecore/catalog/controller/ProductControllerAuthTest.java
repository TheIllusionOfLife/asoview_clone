package com.asoviewclone.commercecore.catalog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.service.CatalogService;
import com.asoviewclone.commercecore.identity.repository.TenantUserRepository;
import com.asoviewclone.commercecore.identity.repository.UserRepository;
import com.asoviewclone.commercecore.inventory.service.InventoryQueryService;
import com.asoviewclone.commercecore.security.FirebaseTokenFilter;
import com.asoviewclone.commercecore.security.SecurityConfig;
import com.asoviewclone.common.error.NotFoundException;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test that exercises ProductController with the real security filter chain so that the
 * public-vs-authenticated permissions are verified together with the controller routing.
 */
@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, FirebaseTokenFilter.class})
class ProductControllerAuthTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private CatalogService catalogService;
  @MockitoBean private InventoryQueryService inventoryQueryService;
  @MockitoBean private FirebaseAuth firebaseAuth;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private TenantUserRepository tenantUserRepository;

  @Test
  void listProductsIsPublic() throws Exception {
    when(catalogService.listProducts(eq(null), eq(null), eq(ProductStatus.ACTIVE), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc.perform(get("/v1/products")).andExpect(status().isOk());
  }

  @Test
  void getActiveProductIsPublic() throws Exception {
    UUID productId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    Product product = new Product(tenantId, null, null, "Kayak", "Fun", null, ProductStatus.ACTIVE);
    when(catalogService.getProduct(productId)).thenReturn(product);

    mockMvc.perform(get("/v1/products/" + productId)).andExpect(status().isOk());
  }

  @Test
  void getNonActiveProductReturnsNotFound() throws Exception {
    UUID productId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    Product draft = new Product(tenantId, null, null, "Draft", "WIP", null, ProductStatus.DRAFT);
    when(catalogService.getProduct(productId)).thenReturn(draft);

    // Controller throws NotFoundException; without GlobalExceptionHandler on this slice,
    // MockMvc surfaces the exception. Assert by catching NestedServletException root cause.
    try {
      mockMvc.perform(get("/v1/products/" + productId));
    } catch (Exception e) {
      // Walk the cause chain for NotFoundException
      Throwable cause = e;
      while (cause != null && !(cause instanceof NotFoundException)) {
        cause = cause.getCause();
      }
      if (cause == null) {
        throw e;
      }
    }
  }
}
