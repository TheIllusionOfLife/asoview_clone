package com.asoviewclone.searchservice.indexer;

import com.asoviewclone.searchservice.query.model.ProductDoc;
import com.google.cloud.discoveryengine.v1.CreateDocumentRequest;
import com.google.cloud.discoveryengine.v1.Document;
import com.google.cloud.discoveryengine.v1.DocumentServiceClient;
import com.google.cloud.discoveryengine.v1.GetDocumentRequest;
import com.google.cloud.discoveryengine.v1.UpdateDocumentRequest;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Struct;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Vertex AI Search implementation of {@link IndexerPort}. Pulls a product from commerce-core and
 * writes it into the Discovery Engine data store's default branch.
 *
 * <p>Popularity updates scope the UpdateDocument call with a FieldMask on {@code
 * struct_data.popularityScore}; concurrent {@code reindex} calls that omit popularityScore (see
 * {@code toDoc}) therefore cannot clobber the sweep-written score.
 *
 * <p>Backfill completion is tracked by a sentinel document with {@code productId =
 * asoview-backfill-marker-v1} and {@code status = MARKER}. The hard {@code status: ANY("ACTIVE")}
 * filter on every user search hides the marker from results.
 */
@Service
@ConditionalOnProperty(name = "search.provider", havingValue = "vertex")
public class VertexAiSearchIndexerService implements IndexerPort {

  private static final Logger log = LoggerFactory.getLogger(VertexAiSearchIndexerService.class);
  private static final String BACKFILL_MARKER_ID = "asoview-backfill-marker-v1";
  private static final String DEFAULT_BRANCH = "default_branch";

  private final DocumentServiceClient documentClient;
  private final RestClient restClient;
  private final ObjectMapper mapper = JsonMapper.builder().build();
  private final String branchName;

  public VertexAiSearchIndexerService(
      DocumentServiceClient documentClient,
      @Value("${commerce-core.base-url:${COMMERCE_CORE_BASE_URL:http://localhost:8080}}")
          String commerceCoreBaseUrl,
      @Value("${vertex.project-id}") String projectId,
      @Value("${vertex.location:global}") String location,
      @Value("${vertex.collection:default_collection}") String collection,
      @Value("${vertex.data-store-id}") String dataStoreId) {
    this.documentClient = documentClient;
    this.restClient = RestClient.builder().baseUrl(commerceCoreBaseUrl).build();
    this.branchName =
        String.format(
            "projects/%s/locations/%s/collections/%s/dataStores/%s/branches/%s",
            projectId, location, collection, dataStoreId, DEFAULT_BRANCH);
  }

  /** Test seam: inject a pre-built RestClient. */
  VertexAiSearchIndexerService(
      DocumentServiceClient documentClient, RestClient restClient, String branchName) {
    this.documentClient = documentClient;
    this.restClient = restClient;
    this.branchName = branchName;
  }

  @Override
  public void reindex(String productId) {
    try {
      String body =
          restClient.get().uri("/v1/products/{id}", productId).retrieve().body(String.class);
      if (body == null || body.isBlank()) {
        log.warn("commerce-core returned empty body for product {}", productId);
        return;
      }
      ProductDoc doc = toDoc(mapper.readTree(body));
      Document document = toDocument(doc);
      upsertDocument(productId, document);
      log.info("Reindexed product {}", productId);
    } catch (Exception e) {
      log.warn("Failed to reindex product {}: {}", productId, e.getMessage());
      throw new RuntimeException("reindex failed: " + productId, e);
    }
  }

  @Override
  public boolean updatePopularityScore(String productId, long score) {
    try {
      String docName = branchName + "/documents/" + productId;
      Struct popularityOnly =
          Struct.newBuilder()
              .putFields(
                  "popularityScore",
                  com.google.protobuf.Value.newBuilder().setNumberValue(score).build())
              .build();
      Document patch =
          Document.newBuilder()
              .setName(docName)
              .setId(productId)
              .setStructData(popularityOnly)
              .build();
      FieldMask mask = FieldMask.newBuilder().addPaths("struct_data.popularityScore").build();
      documentClient.updateDocument(
          UpdateDocumentRequest.newBuilder()
              .setDocument(patch)
              .setUpdateMask(mask)
              .setAllowMissing(false)
              .build());
      return true;
    } catch (Exception e) {
      log.error("Failed to update popularityScore for {}: {}", productId, e.getMessage(), e);
      return false;
    }
  }

  @Override
  public boolean isBackfillComplete() {
    try {
      String docName = branchName + "/documents/" + BACKFILL_MARKER_ID;
      documentClient.getDocument(GetDocumentRequest.newBuilder().setName(docName).build());
      return true;
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
        return false;
      }
      log.warn("isBackfillComplete probe failed: {}", e.getMessage());
      return false;
    } catch (Exception e) {
      log.warn("isBackfillComplete probe failed: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public void markBackfillComplete() {
    Struct data =
        Struct.newBuilder()
            .putFields(
                "productId",
                com.google.protobuf.Value.newBuilder().setStringValue(BACKFILL_MARKER_ID).build())
            .putFields(
                "status", com.google.protobuf.Value.newBuilder().setStringValue("MARKER").build())
            .build();
    Document marker = Document.newBuilder().setId(BACKFILL_MARKER_ID).setStructData(data).build();
    // Propagate the exception so IndexerBackfillJob distinguishes "done" from
    // "marker write failed" and doesn't log a misleading success.
    upsertDocument(BACKFILL_MARKER_ID, marker);
  }

  private void upsertDocument(String documentId, Document document) {
    try {
      documentClient.createDocument(
          CreateDocumentRequest.newBuilder()
              .setParent(branchName)
              .setDocumentId(documentId)
              .setDocument(document)
              .build());
    } catch (Exception createException) {
      if (!isAlreadyExists(createException)) {
        throw createException;
      }
      // Document already exists. Use a FieldMask scoped to the struct fields
      // present in the incoming document so only those fields are overwritten;
      // any absent field (e.g. popularityScore, omitted by toDoc) is preserved
      // server-side. This avoids the extra getDocument round-trip and the race
      // window of read-merge-full-write.
      String docName = branchName + "/documents/" + documentId;
      Document updated = document.toBuilder().setName(docName).build();
      FieldMask mask = buildStructFieldMask(document.getStructData());
      documentClient.updateDocument(
          UpdateDocumentRequest.newBuilder()
              .setDocument(updated)
              .setUpdateMask(mask)
              .setAllowMissing(false)
              .build());
    }
  }

  private static FieldMask buildStructFieldMask(Struct struct) {
    FieldMask.Builder builder = FieldMask.newBuilder();
    for (String field : struct.getFieldsMap().keySet()) {
      builder.addPaths("struct_data." + field);
    }
    return builder.build();
  }

  private static boolean isAlreadyExists(Throwable t) {
    Throwable cur = t;
    while (cur != null) {
      if (cur instanceof StatusRuntimeException sre
          && sre.getStatus().getCode() == Status.Code.ALREADY_EXISTS) {
        return true;
      }
      cur = cur.getCause();
    }
    return false;
  }

  private ProductDoc toDoc(JsonNode node) {
    String id = node.path("id").asText(null);
    String name = node.path("title").asText(null);
    String description = node.path("description").asText(null);
    String areaId = node.path("venueId").asText(null);
    String categoryId = node.path("categoryId").asText(null);
    String status = node.path("status").asText(null);
    Long minPrice = null;
    JsonNode variants = node.path("variants");
    if (variants.isArray()) {
      for (JsonNode v : variants) {
        JsonNode priceNode = v.path("priceAmount");
        if (priceNode.isMissingNode() || priceNode.isNull()) {
          continue;
        }
        Long price = parsePriceAmount(priceNode, id);
        if (price == null) {
          continue;
        }
        if (minPrice == null || price < minPrice) {
          minPrice = price;
        }
      }
    }
    // popularityScore is managed by PopularityScoreSyncJob via updateDocument (field-masked).
    // Passing null here prevents reindex from overwriting the existing score with 0.
    return new ProductDoc(
        id,
        name,
        description,
        areaId,
        categoryId,
        minPrice,
        status,
        Instant.now().toString(),
        null);
  }

  private static Long parsePriceAmount(JsonNode priceNode, String productId) {
    try {
      BigDecimal parsed =
          priceNode.isNumber() ? priceNode.decimalValue() : new BigDecimal(priceNode.asText());
      return parsed.longValueExact();
    } catch (ArithmeticException | NumberFormatException e) {
      log.warn(
          "Skipping malformed priceAmount {} for product {}: {}",
          priceNode,
          productId,
          e.getMessage());
      return null;
    }
  }

  /** Package-private for testing: builds a Discovery Engine Document from a ProductDoc. */
  Document toDocument(ProductDoc doc) {
    Struct.Builder data = Struct.newBuilder();
    putString(data, "productId", doc.productId());
    putString(data, "name", doc.name());
    putString(data, "description", doc.description());
    putString(data, "areaId", doc.areaId());
    putString(data, "categoryId", doc.categoryId());
    putString(data, "status", doc.status());
    if (doc.minPrice() != null) {
      data.putFields(
          "minPrice",
          com.google.protobuf.Value.newBuilder().setNumberValue(doc.minPrice()).build());
    }
    if (doc.popularityScore() != null) {
      data.putFields(
          "popularityScore",
          com.google.protobuf.Value.newBuilder().setNumberValue(doc.popularityScore()).build());
    }
    return Document.newBuilder().setId(doc.productId()).setStructData(data.build()).build();
  }

  private static void putString(Struct.Builder data, String key, String value) {
    if (value != null) {
      data.putFields(key, com.google.protobuf.Value.newBuilder().setStringValue(value).build());
    }
  }
}
