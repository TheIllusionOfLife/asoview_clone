package com.asoviewclone.commercecore.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void healthEndpointIsPublic() throws Exception {
    mockMvc.perform(get("/healthz")).andExpect(status().isOk());
  }

  @Test
  void getCategoriesIsPublic() throws Exception {
    // Returns 200 with empty list since controllers are now registered
    mockMvc.perform(get("/v1/categories")).andExpect(status().isOk());
  }

  @Test
  void getProductsIsPublic() throws Exception {
    mockMvc.perform(get("/v1/products")).andExpect(status().isOk());
  }

  @Test
  void postOrdersRequiresAuth() throws Exception {
    mockMvc
        .perform(post("/v1/orders").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void getMyOrdersRequiresAuth() throws Exception {
    mockMvc.perform(get("/v1/me/orders")).andExpect(status().isForbidden());
  }
}
