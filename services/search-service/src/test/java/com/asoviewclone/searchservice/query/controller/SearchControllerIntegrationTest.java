package com.asoviewclone.searchservice.query.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asoviewclone.searchservice.query.dto.AutosuggestResponse;
import com.asoviewclone.searchservice.query.dto.ProductSearchResponse;
import com.asoviewclone.searchservice.query.model.SearchHit;
import com.asoviewclone.searchservice.query.service.SearchQueryPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-layer integration coverage for /v1/search. Uses @WebMvcTest (slicing out the security,
 * Jackson, and MVC infrastructure) with the SearchQueryPort replaced by a Mockito bean so the test
 * doesn't need a Vertex AI Search client or Workload Identity. Asserts the param-to-service
 * plumbing is correct: default page/size, null-vs-blank query forwarding, filter / sort /
 * pagination params.
 */
@WebMvcTest(SearchController.class)
class SearchControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SearchQueryPort searchQueryService;

  @Test
  void defaultPageAndSizeAreForwarded() throws Exception {
    when(searchQueryService.search(
            eq("onsen"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(0), eq(20)))
        .thenReturn(new ProductSearchResponse(List.of(), 0, 0, 20));

    mockMvc
        .perform(get("/v1/search").param("q", "onsen"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.number").value(0));
  }

  @Test
  void allFilterAndSortParamsFlowThroughToService() throws Exception {
    SearchHit hit =
        new SearchHit("p-1", "Hot Spring Retreat H", "desc", 4800L, "area-kanto", "cat-culture");
    when(searchQueryService.search(
            eq("温泉"),
            eq("area-kanto"),
            eq("cat-culture"),
            eq(1000L),
            eq(5000L),
            eq("price_asc"),
            eq(2),
            eq(25)))
        .thenReturn(new ProductSearchResponse(List.of(hit), 1, 2, 25));

    mockMvc
        .perform(
            get("/v1/search")
                .param("q", "温泉")
                .param("area", "area-kanto")
                .param("category", "cat-culture")
                .param("minPrice", "1000")
                .param("maxPrice", "5000")
                .param("sort", "price_asc")
                .param("page", "2")
                .param("size", "25"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].productId").value("p-1"))
        .andExpect(jsonPath("$.content[0].name").value("Hot Spring Retreat H"))
        .andExpect(jsonPath("$.content[0].minPrice").value(4800))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void missingQueryIsForwardedAsNull() throws Exception {
    // `q` is not required — Vertex treats an empty query as match-all. Verify the
    // controller forwards `null` rather than an empty string so the service's own
    // null-check path fires (setQuery("") branch).
    ArgumentCaptor<String> qCaptor = ArgumentCaptor.forClass(String.class);
    when(searchQueryService.search(
            qCaptor.capture(), eq(null), eq(null), eq(null), eq(null), eq(null), eq(0), eq(20)))
        .thenReturn(new ProductSearchResponse(List.of(), 0, 0, 20));

    mockMvc.perform(get("/v1/search")).andExpect(status().isOk());
    assertThat(qCaptor.getValue()).isNull();
  }

  @Test
  void suggestEndpointRequiresQ() throws Exception {
    mockMvc.perform(get("/v1/search/suggest")).andExpect(status().isBadRequest());
  }

  @Test
  void suggestEndpointReturnsSuggestionsJson() throws Exception {
    when(searchQueryService.suggest(eq("あそ")))
        .thenReturn(
            new AutosuggestResponse(
                List.of(
                    new AutosuggestResponse.Suggestion("p-1", "あそビレッジ"),
                    new AutosuggestResponse.Suggestion("p-2", "あそ温泉"))));

    mockMvc
        .perform(get("/v1/search/suggest").param("q", "あそ"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.suggestions.length()").value(2))
        .andExpect(jsonPath("$.suggestions[0].productId").value("p-1"));
  }
}
