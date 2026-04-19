package com.asoviewclone.searchservice.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asoviewclone.searchservice.query.dto.ProductSearchResponse;
import com.asoviewclone.searchservice.query.model.SearchHit;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.discoveryengine.v1.Document;
import com.google.cloud.discoveryengine.v1.SearchRequest;
import com.google.cloud.discoveryengine.v1.SearchResponse;
import com.google.cloud.discoveryengine.v1.SearchServiceClient;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.util.Arrays;
import java.util.List;
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
  void priceSortIsHandledClientSideNotByOrderBy() {
    // Price sort skips Discovery Engine orderBy entirely — it's applied in
    // Java after fetching a wide window. buildSearchRequest with the price
    // sort value still produces a request without orderBy, and the popularity
    // boost stays attached because applySort returns false.
    SearchRequest req = service.buildSearchRequest("q", null, null, null, null, "price_asc", 0, 20);
    assertThat(req.getOrderBy()).isEmpty();
    assertThat(req.getBoostSpec().getConditionBoostSpecsCount()).isEqualTo(5);
  }

  @Test
  void unknownSortKeyTreatedAsRelevance() {
    SearchRequest req = service.buildSearchRequest("q", null, null, null, null, "name_asc", 0, 20);
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

  // ─── client-side sort ──────────────────────────────────────────────────

  @Test
  void priceComparatorAscSortsNullsLast() {
    List<SearchHit> hits =
        Arrays.asList(hit("c", 3000L), hit("a", null), hit("b", 1000L), hit("d", 2000L));
    hits.sort(VertexAiSearchQueryService.priceComparator("price_asc"));
    assertThat(hits).extracting(SearchHit::productId).containsExactly("b", "d", "c", "a");
  }

  @Test
  void priceComparatorDescSortsNullsLast() {
    List<SearchHit> hits =
        Arrays.asList(hit("c", 3000L), hit("a", null), hit("b", 1000L), hit("d", 2000L));
    hits.sort(VertexAiSearchQueryService.priceComparator("price_desc"));
    assertThat(hits).extracting(SearchHit::productId).containsExactly("c", "d", "b", "a");
  }

  @Test
  void priceAscReturnsMonotonicNonDecreasingHits() {
    SearchResponse response =
        searchResponseOf(docHit("c", 3000L), docHit("a", 1000L), docHit("b", 2000L));
    when(mockClient.search(any(SearchRequest.class)).getPage().getResponse()).thenReturn(response);
    Mockito.clearInvocations(mockClient);

    ProductSearchResponse result = service.search("q", null, null, null, null, "price_asc", 0, 20);

    assertThat(result.content())
        .extracting(SearchHit::minPrice)
        .containsExactly(1000L, 2000L, 3000L);
    // Discovery Engine was called exactly once with pageSize=100, no orderBy.
    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(mockClient, times(1)).search(captor.capture());
    SearchRequest emitted = captor.getValue();
    assertThat(emitted.getOrderBy()).isEmpty();
    assertThat(emitted.getPageSize()).isEqualTo(VertexAiSearchQueryService.CLIENT_SORT_WINDOW);
    assertThat(emitted.getOffset()).isZero();
  }

  @Test
  void priceDescReturnsMonotonicNonIncreasingHits() {
    SearchResponse response =
        searchResponseOf(docHit("a", 1000L), docHit("c", 3000L), docHit("b", 2000L));
    when(mockClient.search(any(SearchRequest.class)).getPage().getResponse()).thenReturn(response);
    Mockito.clearInvocations(mockClient);

    ProductSearchResponse result = service.search("q", null, null, null, null, "price_desc", 0, 20);

    assertThat(result.content())
        .extracting(SearchHit::minPrice)
        .containsExactly(3000L, 2000L, 1000L);
  }

  @Test
  void clientSideSortAppliesCallerPaginationToSortedList() {
    SearchResponse response =
        searchResponseOf(
            docHit("e", 5000L),
            docHit("a", 1000L),
            docHit("d", 4000L),
            docHit("b", 2000L),
            docHit("c", 3000L));
    when(mockClient.search(any(SearchRequest.class)).getPage().getResponse()).thenReturn(response);
    Mockito.clearInvocations(mockClient);

    // page=1, size=2 over globally-sorted [1000, 2000, 3000, 4000, 5000] → [3000, 4000].
    ProductSearchResponse result = service.search("q", null, null, null, null, "price_asc", 1, 2);

    assertThat(result.content()).extracting(SearchHit::minPrice).containsExactly(3000L, 4000L);
    assertThat(result.number()).isEqualTo(1);
    assertThat(result.size()).isEqualTo(2);
  }

  // ─── defense-in-depth: orderBy fallback still fires for non-price sorts ───

  @Test
  void searchRetriesWithoutSortWhenVertexRejectsOrderByForNonPriceSort() {
    // Client-side sort only intercepts price_asc/price_desc. For any other
    // non-null sort value a future commit might set via applySort(), the
    // isInvalidOrderBy fallback must still catch an orderBy rejection.
    SearchResponse empty = SearchResponse.getDefaultInstance();
    when(mockClient.search(any(SearchRequest.class)).getPage().getResponse())
        .thenThrow(invalidArgument("Unsupported field in orderBy: rating asc"))
        .thenReturn(empty);
    Mockito.clearInvocations(mockClient);

    ProductSearchResponse result = service.search("q", null, null, null, null, "rating_asc", 0, 20);
    assertThat(result).isNotNull();
    verify(mockClient, times(2)).search(any(SearchRequest.class));
  }

  @Test
  void searchPropagatesInvalidArgumentThatIsUnrelatedToOrderBy() {
    when(mockClient.search(any(SearchRequest.class)).getPage().getResponse())
        .thenThrow(invalidArgument("Invalid filter expression: foo"));
    Mockito.clearInvocations(mockClient);

    assertThatThrownBy(() -> service.search("q", null, null, null, null, "rating_asc", 0, 20))
        .isInstanceOf(RuntimeException.class);
    verify(mockClient, times(1)).search(any(SearchRequest.class));
  }

  // ─── helpers ───────────────────────────────────────────────────────────

  private static SearchHit hit(String id, Long price) {
    return new SearchHit(id, "name-" + id, "desc", price, "area-x", "cat-y");
  }

  private static SearchResponse.SearchResult docHit(String id, Long price) {
    Struct.Builder struct =
        Struct.newBuilder()
            .putFields("productId", Value.newBuilder().setStringValue(id).build())
            .putFields("name", Value.newBuilder().setStringValue("name-" + id).build())
            .putFields("description", Value.newBuilder().setStringValue("desc").build())
            .putFields("areaId", Value.newBuilder().setStringValue("area-x").build())
            .putFields("categoryId", Value.newBuilder().setStringValue("cat-y").build());
    if (price != null) {
      struct.putFields("minPrice", Value.newBuilder().setNumberValue(price).build());
    }
    return SearchResponse.SearchResult.newBuilder()
        .setDocument(Document.newBuilder().setStructData(struct.build()).build())
        .build();
  }

  private static SearchResponse searchResponseOf(SearchResponse.SearchResult... results) {
    SearchResponse.Builder b = SearchResponse.newBuilder();
    for (SearchResponse.SearchResult r : results) {
      b.addResults(r);
    }
    return b.setTotalSize(results.length).build();
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
