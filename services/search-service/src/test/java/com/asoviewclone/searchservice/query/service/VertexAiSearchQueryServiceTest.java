package com.asoviewclone.searchservice.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asoviewclone.searchservice.query.dto.ProductSearchResponse;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.discoveryengine.v1.SearchRequest;
import com.google.cloud.discoveryengine.v1.SearchResponse;
import com.google.cloud.discoveryengine.v1.SearchServiceClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class VertexAiSearchQueryServiceTest {

  private final SearchServiceClient mockClient =
      Mockito.mock(SearchServiceClient.class, Mockito.RETURNS_DEEP_STUBS);
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
    // `price` is the key property Vertex recognizes for sort on generic
    // vertical; the schema's `keyPropertyMapping: price` on `minPrice` wires
    // our field into that key.
    assertThat(req.getOrderBy()).isEqualTo("price asc");
    assertThat(req.getBoostSpec().getConditionBoostSpecsCount()).isZero();
  }

  @Test
  void priceDescSortSetsOrderByAndSkipsBoostSpec() {
    SearchRequest req =
        service.buildSearchRequest("q", null, null, null, null, "price_desc", 0, 20);
    assertThat(req.getOrderBy()).isEqualTo("price desc");
  }

  @Test
  void unknownSortKeyTreatedAsRelevance() {
    SearchRequest req = service.buildSearchRequest("q", null, null, null, null, "name_asc", 0, 20);
    // `name_asc` is no longer supported (Vertex generic vertical only sorts
    // on predefined key properties). Unknown sort values fall through to
    // relevance with the popularity boost re-attached.
    assertThat(req.getOrderBy()).isEmpty();
    assertThat(req.getBoostSpec().getConditionBoostSpecsCount()).isEqualTo(5);
  }

  @Test
  void relevanceSortAttachesPopularityBoostSpec() {
    SearchRequest req = service.buildSearchRequest("q", null, null, null, null, null, 0, 20);
    assertThat(req.getOrderBy()).isEmpty();
    assertThat(req.getBoostSpec().getConditionBoostSpecsCount()).isEqualTo(5);
    assertThat(req.getBoostSpec().getConditionBoostSpecs(0).getCondition())
        .contains("popularityScore");
  }

  @Test
  void searchRetriesWithoutSortWhenVertexRejectsOrderBy() {
    // First call (with orderBy) throws INVALID_ARGUMENT; fallback call
    // (without orderBy) succeeds. Service must swallow the first error,
    // emit a second request with no orderBy, and return the success body.
    SearchResponse empty = SearchResponse.getDefaultInstance();
    when(mockClient.search(any(SearchRequest.class)).getPage().getResponse())
        .thenThrow(invalidArgument("Unsupported field in orderBy: minPrice asc"))
        .thenReturn(empty);
    // The deep-stub chain above records one invocation on `search`; reset so
    // the subsequent verify counts only the real service calls.
    Mockito.clearInvocations(mockClient);

    ProductSearchResponse result = service.search("q", null, null, null, null, "price_asc", 0, 20);
    assertThat(result).isNotNull();

    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(mockClient, times(2)).search(captor.capture());
    // First request carries the orderBy; second is the relevance fallback.
    assertThat(captor.getAllValues().get(0).getOrderBy()).isEqualTo("price asc");
    assertThat(captor.getAllValues().get(1).getOrderBy()).isEmpty();
  }

  @Test
  void searchPropagatesInvalidArgumentThatIsUnrelatedToOrderBy() {
    // INVALID_ARGUMENT on filter (or any other reason) must NOT trigger
    // the fallback — the bug would otherwise be masked and hard to debug.
    when(mockClient.search(any(SearchRequest.class)).getPage().getResponse())
        .thenThrow(invalidArgument("Invalid filter expression: foo"));
    Mockito.clearInvocations(mockClient);

    assertThatThrownBy(() -> service.search("q", null, null, null, null, "price_asc", 0, 20))
        .isInstanceOf(RuntimeException.class);
    // Exactly one attempt; no fallback retry.
    verify(mockClient, times(1)).search(any(SearchRequest.class));
  }

  private static ApiException invalidArgument(String message) {
    StatusCode code =
        new StatusCode() {
          @Override
          public StatusCode.Code getCode() {
            return StatusCode.Code.INVALID_ARGUMENT;
          }

          @Override
          public Object getTransportCode() {
            return null;
          }
        };
    return new ApiException(message, null, code, false);
  }
}
