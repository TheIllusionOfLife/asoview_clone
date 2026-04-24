package com.asoviewclone.commercecore.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    // 401 (not 403) is the correct shape: the request is unauthenticated,
    // so Spring Security hands control to the AuthenticationEntryPoint,
    // which we've wired to JsonAuthenticationEntryPoint (see SecurityConfig).
    // The older default (Http403ForbiddenEntryPoint emitting an empty 403)
    // was wrong: 403 is "authenticated but lacking permission". Clients
    // that branch on status can now distinguish "log in" from "access denied".
    mockMvc
        .perform(post("/v1/orders").contentType("application/json").content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("\"error\":\"UNAUTHORIZED\"")));
  }

  @Test
  void getMyOrdersRequiresAuth() throws Exception {
    // See postOrdersRequiresAuth for the 401-vs-403 rationale.
    mockMvc
        .perform(get("/v1/me/orders"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("\"error\":\"UNAUTHORIZED\"")));
  }
}
