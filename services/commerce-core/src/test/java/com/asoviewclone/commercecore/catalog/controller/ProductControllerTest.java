package com.asoviewclone.commercecore.catalog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.service.CatalogService;
import com.asoviewclone.commercecore.identity.repository.TenantUserRepository;
import com.asoviewclone.commercecore.identity.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private CatalogService catalogService;
  @MockitoBean private FirebaseAuth firebaseAuth;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private TenantUserRepository tenantUserRepository;

  @Test
  void listProductsReturnsPage() throws Exception {
    UUID tenantId = UUID.randomUUID();
    Product product =
        new Product(tenantId, null, null, "Rafting Tour", "Fun!", null, ProductStatus.ACTIVE);
    when(catalogService.listProducts(eq(null), eq(null), any()))
        .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(get("/v1/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].title").value("Rafting Tour"));
  }

  @Test
  void getProductReturnsProduct() throws Exception {
    UUID productId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    Product product =
        new Product(
            tenantId, null, null, "Kayak Adventure", "Amazing!", null, ProductStatus.ACTIVE);
    when(catalogService.getProduct(productId)).thenReturn(product);

    mockMvc
        .perform(get("/v1/products/" + productId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Kayak Adventure"));
  }
}
