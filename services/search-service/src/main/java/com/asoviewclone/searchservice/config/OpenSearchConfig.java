package com.asoviewclone.searchservice.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenSearch client wiring. Uses {@link RestHighLevelClient} for now; the high-level client is in
 * maintenance mode and slated for removal in OpenSearch 3.0. Migration to the new {@code
 * opensearch-java} client is tracked as a follow-up (PR #21 review C7 from CodeRabbit) — the
 * migration touches every query in {@code SearchQueryService} so it gets its own PR.
 *
 * <p>Connect/socket timeouts are wired here so a slow OpenSearch cannot indefinitely block caller
 * threads. Auth (TLS + admin user from a Secret Manager-backed Spring Cloud Config) lands with the
 * security-enabled rollout in PR 3e.
 */
@Configuration
public class OpenSearchConfig {

  @Value("${opensearch.host:localhost}")
  private String host;

  @Value("${opensearch.port:9200}")
  private int port;

  @Value("${opensearch.scheme:http}")
  private String scheme;

  @Value("${opensearch.connect-timeout-ms:5000}")
  private int connectTimeoutMs;

  @Value("${opensearch.socket-timeout-ms:30000}")
  private int socketTimeoutMs;

  @Bean(destroyMethod = "close")
  public RestHighLevelClient openSearchClient() {
    RestClientBuilder builder =
        RestClient.builder(new HttpHost(scheme, host, port))
            .setRequestConfigCallback(
                (RequestConfig.Builder cfg) ->
                    cfg.setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                        .setResponseTimeout(Timeout.ofMilliseconds(socketTimeoutMs)));
    return new RestHighLevelClient(builder);
  }
}
