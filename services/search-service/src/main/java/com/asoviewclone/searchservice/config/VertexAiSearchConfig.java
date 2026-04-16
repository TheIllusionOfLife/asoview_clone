package com.asoviewclone.searchservice.config;

import com.google.cloud.discoveryengine.v1.DocumentServiceClient;
import com.google.cloud.discoveryengine.v1.SchemaServiceClient;
import com.google.cloud.discoveryengine.v1.SearchServiceClient;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Vertex AI Search (Discovery Engine API) client beans. Activates only when {@code
 * search.provider=vertex}, so the OpenSearch path is untouched on every other deployment.
 *
 * <p>Authentication uses Application Default Credentials — on GKE that resolves to the Workload
 * Identity GSA {@code search-service-vertex@asoview-clone-dev.iam.gserviceaccount.com} bound to the
 * KSA {@code search/search-service}.
 */
@Configuration
@ConditionalOnProperty(name = "search.provider", havingValue = "vertex")
public class VertexAiSearchConfig {

  @Bean(destroyMethod = "close")
  public SearchServiceClient vertexSearchServiceClient() throws IOException {
    return SearchServiceClient.create();
  }

  @Bean(destroyMethod = "close")
  public DocumentServiceClient vertexDocumentServiceClient() throws IOException {
    return DocumentServiceClient.create();
  }

  @Bean(destroyMethod = "close")
  public SchemaServiceClient vertexSchemaServiceClient() throws IOException {
    return SchemaServiceClient.create();
  }
}
