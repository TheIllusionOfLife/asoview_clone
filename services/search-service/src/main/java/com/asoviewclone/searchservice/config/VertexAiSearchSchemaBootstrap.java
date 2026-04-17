package com.asoviewclone.searchservice.config;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.discoveryengine.v1.Schema;
import com.google.cloud.discoveryengine.v1.SchemaServiceClient;
import com.google.cloud.discoveryengine.v1.UpdateSchemaMetadata;
import com.google.cloud.discoveryengine.v1.UpdateSchemaRequest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Applies {@code vertex/products-schema.json} to the Discovery Engine data store's default schema
 * on pod startup. Runs before {@link com.asoviewclone.searchservice.indexer.IndexerBackfillJob}
 * (@Order(100)) so the backfill never runs against a partial schema.
 *
 * <p>If the schema update fails, the exception is rethrown so the pod crashes — better to
 * CrashLoopBackOff than to silently run the backfill against a broken schema.
 *
 * <p>Fail-closed default: {@code vertex.schema.bootstrap} is {@code false} at every property
 * resolution layer (application.yml default, @Value fallback). Each environment opts in explicitly
 * via the {@code VERTEX_SCHEMA_BOOTSTRAP=true} env var so an accidentally-deployed pod cannot
 * silently mutate a data-store schema it shouldn't own.
 */
@Component
@Order(50)
public class VertexAiSearchSchemaBootstrap implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(VertexAiSearchSchemaBootstrap.class);
  private static final String SCHEMA_RESOURCE = "vertex/products-schema.json";
  private static final String DEFAULT_SCHEMA_ID = "default_schema";

  private final SchemaServiceClient schemaClient;
  private final String projectId;
  private final String location;
  private final String collection;
  private final String dataStoreId;
  private final boolean bootstrapEnabled;

  public VertexAiSearchSchemaBootstrap(
      SchemaServiceClient schemaClient,
      @Value("${vertex.project-id}") String projectId,
      @Value("${vertex.location:global}") String location,
      @Value("${vertex.collection:default_collection}") String collection,
      @Value("${vertex.data-store-id}") String dataStoreId,
      @Value("${vertex.schema.bootstrap:false}") boolean bootstrapEnabled) {
    this.schemaClient = schemaClient;
    this.projectId = projectId;
    this.location = location;
    this.collection = collection;
    this.dataStoreId = dataStoreId;
    this.bootstrapEnabled = bootstrapEnabled;
  }

  @Override
  public void run(String... args) throws Exception {
    if (!bootstrapEnabled) {
      log.info("Vertex schema bootstrap disabled via vertex.schema.bootstrap=false");
      return;
    }

    String jsonSchema;
    try (var in = new ClassPathResource(SCHEMA_RESOURCE).getInputStream()) {
      jsonSchema = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
    }

    String schemaName =
        String.format(
            "projects/%s/locations/%s/collections/%s/dataStores/%s/schemas/%s",
            projectId, location, collection, dataStoreId, DEFAULT_SCHEMA_ID);
    Schema schema = Schema.newBuilder().setName(schemaName).setJsonSchema(jsonSchema).build();
    UpdateSchemaRequest request =
        UpdateSchemaRequest.newBuilder().setSchema(schema).setAllowMissing(true).build();

    log.info("Applying Vertex AI Search schema to data store {}", dataStoreId);
    OperationFuture<Schema, UpdateSchemaMetadata> op = schemaClient.updateSchemaAsync(request);
    try {
      op.get(60, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      op.cancel(true);
      throw new RuntimeException("Schema update timed out after 60 s", e);
    } catch (InterruptedException e) {
      op.cancel(true);
      Thread.currentThread().interrupt();
      throw new RuntimeException("Schema update interrupted", e);
    }
    log.info("Vertex AI Search schema applied to data store {}", dataStoreId);
  }
}
