package com.asoviewclone.searchservice.query.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.discoveryengine.v1.SearchRequest;
import com.google.cloud.discoveryengine.v1.SearchServiceClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class VertexAiSearchQueryServiceTest {

  private final SearchServiceClient mockClient = Mockito.mock(SearchServiceClient.class);
  private final VertexAiSearchQueryService service =
      new VertexAiSearchQueryService(
          mockClient,
          "proj",
          "global",
          "default_collection",
          "asoview-products-engine",
          "default_search");

  @Test
  void buildsExpectedServingConfigPath() {
    SearchRequest req = service.buildSearchRequest(null, null, null, null, null, null, 0, 20);
    assertThat(req.getServingConfig())
        .isEqualTo(
            "projects/proj/locations/global/collections/default_collection/engines/asoview-products-engine/servingConfigs/default_search");
  }

  @Test
  void alwaysInjectsStatusActiveFilter() {
    SearchRequest req = service.buildSearchRequest("onsen", null, null, null, null, null, 0, 20);
    assertThat(req.getFilter()).contains("status: ANY(\"ACTIVE\")");
  }

  @Test
  void composesAreaCategoryAndPriceFilters() {
    SearchRequest req =
        service.buildSearchRequest("onsen", "area-kanto", "cat-spa", 1000L, 5000L, null, 0, 20);
    String filter = req.getFilter();
    assertThat(filter).contains("areaId: ANY(\"area-kanto\")");
    assertThat(filter).contains("categoryId: ANY(\"cat-spa\")");
    assertThat(filter).contains("minPrice >= 1000");
    assertThat(filter).contains("minPrice <= 5000");
  }

  @Test
  void escapesDoubleQuotesInFilterValues() {
    SearchRequest req = service.buildSearchRequest("x", "he\"llo", null, null, null, null, 0, 20);
    assertThat(req.getFilter()).contains("areaId: ANY(\"he\\\"llo\")");
  }

  @Test
  void emptyQueryYieldsMatchAllEquivalent() {
    SearchRequest req = service.buildSearchRequest(null, null, null, null, null, null, 0, 20);
    assertThat(req.getQuery()).isEmpty();
  }

  @Test
  void computesOffsetFromPageAndSize() {
    SearchRequest req = service.buildSearchRequest("q", null, null, null, null, null, 3, 25);
    assertThat(req.getOffset()).isEqualTo(75);
    assertThat(req.getPageSize()).isEqualTo(25);
  }

  @Test
  void priceAscSortSetsOrderByAndSkipsBoostSpec() {
    SearchRequest req = service.buildSearchRequest("q", null, null, null, null, "price_asc", 0, 20);
    assertThat(req.getOrderBy()).isEqualTo("structData.minPrice asc");
    assertThat(req.getBoostSpec().getConditionBoostSpecsCount()).isZero();
  }

  @Test
  void priceDescSortSetsOrderByAndSkipsBoostSpec() {
    SearchRequest req =
        service.buildSearchRequest("q", null, null, null, null, "price_desc", 0, 20);
    assertThat(req.getOrderBy()).isEqualTo("structData.minPrice desc");
  }

  @Test
  void nameAscSortSetsOrderBy() {
    SearchRequest req = service.buildSearchRequest("q", null, null, null, null, "name_asc", 0, 20);
    assertThat(req.getOrderBy()).isEqualTo("structData.name asc");
  }

  @Test
  void relevanceSortAttachesPopularityBoostSpec() {
    SearchRequest req = service.buildSearchRequest("q", null, null, null, null, null, 0, 20);
    assertThat(req.getOrderBy()).isEmpty();
    assertThat(req.getBoostSpec().getConditionBoostSpecsCount()).isEqualTo(5);
    assertThat(req.getBoostSpec().getConditionBoostSpecs(0).getCondition())
        .contains("popularityScore");
  }
}
