package com.asoviewclone.searchservice.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.searchservice.query.dto.ProductSearchResponse;
import com.asoviewclone.searchservice.query.service.SearchQueryService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.testcontainers.OpenSearchContainer;
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
class IndexerServiceTest {

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
  static final OpenSearchContainer<?> OPENSEARCH = new OpenSearchContainer<>(OPENSEARCH_IMAGE);

  static HttpServer stubServer;

  @BeforeAll
  static void startStub() throws Exception {
    stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    stubServer.createContext(
        "/v1/products/p-stub",
        exchange -> {
          String body =
              "{\"id\":\"p-stub\",\"title\":\"テスト商品\",\"description\":\"説明文\","
                  + "\"venueId\":\"area-1\",\"categoryId\":\"cat-1\",\"status\":\"ACTIVE\","
                  + "\"variants\":[{\"priceAmount\":1500},{\"priceAmount\":2500}]}";
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    stubServer.start();
  }

  @AfterAll
  static void stopStub() {
    if (stubServer != null) {
      stubServer.stop(0);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("opensearch.host", OPENSEARCH::getHost);
    registry.add("opensearch.port", () -> OPENSEARCH.getMappedPort(9200));
    registry.add("opensearch.scheme", () -> "http");
    registry.add(
        "commerce-core.base-url", () -> "http://127.0.0.1:" + stubServer.getAddress().getPort());
  }

  @Autowired private RestHighLevelClient client;
  @Autowired private IndexerService indexerService;
  @Autowired private SearchQueryService searchQueryService;

  @Test
  void reindexPullsFromCommerceCoreAndIndexes() throws Exception {
    // ensure index exists fresh
    try {
      client.getLowLevelClient().performRequest(new Request("DELETE", "/" + INDEX));
    } catch (Exception ignored) {
      // first run
    }

    indexerService.reindex("p-stub");
    client.getLowLevelClient().performRequest(new Request("POST", "/" + INDEX + "/_refresh"));

    ProductSearchResponse resp =
        searchQueryService.search("テスト", null, null, null, null, null, 0, 10);
    assertThat(resp.totalElements()).isEqualTo(1);
    assertThat(resp.content().get(0).productId()).isEqualTo("p-stub");
    assertThat(resp.content().get(0).minPrice()).isEqualTo(1500L);
  }
}
