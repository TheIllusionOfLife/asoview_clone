package com.asoviewclone.searchservice.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.searchservice.query.dto.AutosuggestResponse;
import com.asoviewclone.searchservice.query.dto.ProductSearchResponse;
import com.asoviewclone.searchservice.query.service.SearchQueryService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"search.index-name=asoview-products-test", "search.backfill.enabled=false"})
class SearchQueryServiceTest {

  private static final String INDEX = "asoview-products-test";

  private static final DockerImageName OPENSEARCH_IMAGE =
      DockerImageName.parse(
              new ImageFromDockerfile("opensearch-kuromoji-test", false)
                  .withDockerfileFromBuilder(
                      b ->
                          b.from("opensearchproject/opensearch:2.18.0")
                              .run(
                                  "/usr/share/opensearch/bin/opensearch-plugin install --batch"
                                      + " analysis-kuromoji")
                              .build())
                  .get())
          .asCompatibleSubstituteFor("opensearchproject/opensearch");

  @Container
  static final OpensearchContainer<?> OPENSEARCH = new OpensearchContainer<>(OPENSEARCH_IMAGE);

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("opensearch.host", OPENSEARCH::getHost);
    registry.add("opensearch.port", () -> OPENSEARCH.getMappedPort(9200));
    registry.add("opensearch.scheme", () -> "http");
  }

  @Autowired private RestHighLevelClient client;
  @Autowired private SearchQueryService searchQueryService;

  @BeforeAll
  static void waitForTemplate() {
    // IndexTemplateBootstrap runs in CommandLineRunner, so by the time @Autowired beans
    // are injected the template is installed.
  }

  @org.junit.jupiter.api.BeforeEach
  void seed() throws Exception {
    // Recreate the index from scratch each test for isolation.
    try {
      client.getLowLevelClient().performRequest(new Request("DELETE", "/" + INDEX));
    } catch (Exception ignored) {
      // first run — index doesn't exist
    }
    indexDoc("p1", "東京ダイビング体験ツアー", "初心者向けの体験ダイビングです", "A1", "C1", 8000);
    indexDoc("p2", "沖縄ダイビングライセンス講習", "PADIライセンス取得コース", "A2", "C1", 45000);
    indexDoc("p3", "東京スカイツリー展望台チケット", "東京の人気観光スポット", "A1", "C2", 2500);
    indexDoc("p4", "京都伝統茶道体験", "本格的な茶道を体験", "A3", "C2", 6000);
    indexDoc("p5", "大阪城見学ツアー", "歴史ガイド付き", "A4", "C2", 3000);
    // refresh
    client.getLowLevelClient().performRequest(new Request("POST", "/" + INDEX + "/_refresh"));
  }

  private void indexDoc(
      String id, String name, String desc, String area, String category, long price)
      throws Exception {
    Request req = new Request("POST", "/" + INDEX + "/_doc/" + id);
    req.setJsonEntity(
        String.format(
            "{\"productId\":\"%s\",\"name\":\"%s\",\"description\":\"%s\","
                + "\"areaId\":\"%s\",\"categoryId\":\"%s\",\"minPrice\":%d,"
                + "\"status\":\"ACTIVE\",\"indexedAt\":\"2026-04-07T00:00:00Z\"}",
            id, name, desc, area, category, price));
    client.getLowLevelClient().performRequest(req);
  }

  @Test
  void textSearchMatchesJapaneseWithKuromoji() {
    ProductSearchResponse resp =
        searchQueryService.search("ダイビング", null, null, null, null, null, 0, 20);
    assertThat(resp.totalElements()).isEqualTo(2);
    assertThat(resp.content()).extracting("productId").containsExactlyInAnyOrder("p1", "p2");
  }

  @Test
  void areaFilterNarrows() {
    ProductSearchResponse resp =
        searchQueryService.search(null, "A1", null, null, null, null, 0, 20);
    assertThat(resp.totalElements()).isEqualTo(2);
    assertThat(resp.content()).extracting("productId").containsExactlyInAnyOrder("p1", "p3");
  }

  @Test
  void categoryFilterNarrows() {
    ProductSearchResponse resp =
        searchQueryService.search(null, null, "C1", null, null, null, 0, 20);
    assertThat(resp.totalElements()).isEqualTo(2);
    assertThat(resp.content()).extracting("productId").containsExactlyInAnyOrder("p1", "p2");
  }

  @Test
  void priceRangeFilter() {
    ProductSearchResponse resp =
        searchQueryService.search(null, null, null, 5000L, 10000L, null, 0, 20);
    assertThat(resp.content()).extracting("productId").containsExactlyInAnyOrder("p1", "p4");
  }

  @Test
  void sortPriceAsc() {
    ProductSearchResponse resp =
        searchQueryService.search(null, null, null, null, null, "price_asc", 0, 20);
    assertThat(resp.content())
        .extracting("productId")
        .containsExactly("p3", "p5", "p4", "p1", "p2");
  }

  @Test
  void paginationSecondPage() {
    ProductSearchResponse resp =
        searchQueryService.search(null, null, null, null, null, "price_asc", 1, 2);
    assertThat(resp.number()).isEqualTo(1);
    assertThat(resp.size()).isEqualTo(2);
    assertThat(resp.content()).extracting("productId").containsExactly("p4", "p1");
  }

  @Test
  void autosuggestPrefixMatch() {
    AutosuggestResponse resp = searchQueryService.suggest("東京");
    assertThat(resp.suggestions()).extracting("productId").contains("p1", "p3");
  }
}
