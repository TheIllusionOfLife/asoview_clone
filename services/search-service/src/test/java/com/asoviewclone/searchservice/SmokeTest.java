package com.asoviewclone.searchservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SmokeTest {

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

  @Test
  void templateInstalledAndSearchable() throws Exception {
    Request getTpl = new Request("GET", "/_index_template/asoview-products");
    Response tplResp = client.getLowLevelClient().performRequest(getTpl);
    assertThat(tplResp.getStatusLine().getStatusCode()).isEqualTo(200);

    Request index = new Request("POST", "/asoview-products-test/_doc/1?refresh=true");
    index.setJsonEntity(
        "{\"productId\":\"p1\",\"areaId\":\"a1\",\"categoryId\":\"c1\","
            + "\"status\":\"ACTIVE\",\"name\":\"東京スカイツリー展望台チケット\","
            + "\"description\":\"東京の人気観光スポット\",\"minPrice\":2500,"
            + "\"indexedAt\":\"2026-04-07T00:00:00Z\"}");
    Response indexResp = client.getLowLevelClient().performRequest(index);
    assertThat(indexResp.getStatusLine().getStatusCode()).isBetween(200, 299);

    Request search = new Request("GET", "/asoview-products-test/_search");
    search.setJsonEntity("{\"query\":{\"match\":{\"name\":\"スカイツリー\"}}}");
    Response searchResp = client.getLowLevelClient().performRequest(search);
    String body =
        new String(searchResp.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(body).contains("\"productId\":\"p1\"");
  }
}
