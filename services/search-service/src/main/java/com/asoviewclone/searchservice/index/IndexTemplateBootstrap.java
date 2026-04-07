package com.asoviewclone.searchservice.index;

import java.nio.charset.StandardCharsets;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class IndexTemplateBootstrap implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(IndexTemplateBootstrap.class);
  private static final String TEMPLATE_NAME = "asoview-products";
  private static final String TEMPLATE_RESOURCE = "opensearch/products-index-template.json";

  private final RestHighLevelClient client;

  public IndexTemplateBootstrap(RestHighLevelClient client) {
    this.client = client;
  }

  @Override
  public void run(String... args) {
    try {
      Request check = new Request("GET", "/_index_template/" + TEMPLATE_NAME);
      try {
        Response resp = client.getLowLevelClient().performRequest(check);
        if (resp.getStatusLine().getStatusCode() == 200) {
          log.info("Index template {} already exists, skipping install", TEMPLATE_NAME);
          return;
        }
      } catch (Exception ignored) {
        // 404 or transient — attempt install below
      }

      String body;
      try (var in = new ClassPathResource(TEMPLATE_RESOURCE).getInputStream()) {
        body = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
      }

      Request put = new Request("PUT", "/_index_template/" + TEMPLATE_NAME);
      put.setJsonEntity(body);
      Response putResp = client.getLowLevelClient().performRequest(put);
      log.info(
          "Installed index template {} ({})",
          TEMPLATE_NAME,
          putResp.getStatusLine().getStatusCode());
    } catch (Exception e) {
      log.warn(
          "Failed to install index template {} on startup: {}. Will retry later.",
          TEMPLATE_NAME,
          e.getMessage());
    }
  }
}
