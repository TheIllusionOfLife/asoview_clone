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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Vertex AI Search implementation of {@link IndexerPort}. Pulls a product from commerce-core and
 * writes it into the Discovery Engine data store's default branch.
 *
 * <p>Discovery Engine rejects subpath FieldMasks into {@code struct_data} ({@code INVALID_ARGUMENT:
 * Invalid update_mask.paths: struct_data.<field>}). {@code google.protobuf.Struct} is a map, and
 * FieldMask only addresses named proto fields. So both update paths use the whole {@code
 * struct_data} path and replace the struct atomically. Both paths also read-merge-write the other's
 * owned fields so neither side silently drops data under normal (non-concurrent) operation:
 *
 * <ul>
 *   <li>{@code reindex} / upsert: {@code toDoc} carries every product-owned struct field but passes
 *       {@code popularityScore=null}. On the update branch we first {@code GetDocument} and copy
 *       the existing {@code popularityScore} into the outgoing struct before writing.
 *   <li>{@code updatePopularityScore}: we first {@code GetDocument}, overwrite only {@code
 *       popularityScore} on the existing struct, then write the merged struct back.
 * </ul>
 *
 * <p>Discovery Engine has no CAS / ETag for documents, so a narrow last-writer-wins window between
 * each path's read and write remains: a reindex + popularity update racing at the millisecond level
 * can still revert a field the loser had just written. Accepted — popularity is a soft metric,
 * product updates are infrequent, and stronger semantics would require an explicit version field +
 * retry loop.
 *
 * <p>Backfill completion is tracked by a sentinel document with {@code productId =
 * asoview-backfill-marker-v1} and {@code status = MARKER}. The hard {@code status: ANY("ACTIVE")}
 * filter on every user search hides the marker from results.
 */
@Service
public class VertexAiSearchIndexerService implements IndexerPort {

  private static final Logger log = LoggerFactory.getLogger(VertexAiSearchIndexerService.class);
  private static final String BACKFILL_MARKER_ID = "asoview-backfill-marker-v1";
  private static final String DEFAULT_BRANCH = "default_branch";

  private final DocumentServiceClient documentClient;
  private final RestClient restClient;
  private final ObjectMapper mapper = JsonMapper.builder().build();
  private final String branchName;

  @Autowired
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
      // Discovery Engine has no partial-struct patch API: a subpath mask like
      // `struct_data.popularityScore` is rejected as INVALID_ARGUMENT. The only
      // writable unit is the whole `struct_data` path. So read-merge-write:
      // fetch the current struct, overwrite only popularityScore, push the
      // merged struct back with mask=["struct_data"].
      //
      // Symmetry with upsertDocument: both paths read-merge-write so neither
      // silently drops data on a sequential call. A narrow millisecond-level
      // race between reads and writes across both paths remains (no CAS /
      // ETag on Discovery Engine documents); accepted.
      Document existing =
          documentClient.getDocument(GetDocumentRequest.newBuilder().setName(docName).build());
      Struct merged =
          existing.getStructData().toBuilder()
              .putFields(
                  "popularityScore",
                  com.google.protobuf.Value.newBuilder().setNumberValue(score).build())
              .build();
      Document patch =
          Document.newBuilder().setName(docName).setId(productId).setStructData(merged).build();
      FieldMask mask = FieldMask.newBuilder().addPaths("struct_data").build();
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
      // Document already exists. Replace the whole struct_data atomically.
      // Subpath masks like `struct_data.<key>` are rejected by Discovery
      // Engine (Struct is a map; FieldMask only addresses named proto fields).
      // To avoid dropping popularityScore (owned by updatePopularityScore)
      // when toDoc doesn't carry it, read the existing document first and
      // merge its popularityScore into the incoming struct — the reindex
      // path thus stays symmetric with updatePopularityScore (both paths
      // read-merge-write the whole struct). Discovery Engine has no CAS /
      // ETag for documents, so a narrow last-writer-wins window between
      // read and write remains; accepted.
      String docName = branchName + "/documents/" + documentId;
      Document updated =
          document.toBuilder()
              .setName(docName)
              .setStructData(mergePreservingPopularity(docName, document.getStructData()))
              .build();
      FieldMask mask = FieldMask.newBuilder().addPaths("struct_data").build();
      documentClient.updateDocument(
          UpdateDocumentRequest.newBuilder()
              .setDocument(updated)
              .setUpdateMask(mask)
              .setAllowMissing(false)
              .build());
    }
  }

  /**
   * Returns {@code incoming} with {@code popularityScore} copied from the server's current
   * document, if incoming omits it. If incoming already carries popularityScore (e.g. tests pass
   * one in explicitly) it wins. If the server has no popularityScore yet, the returned struct is
   * unchanged.
   */
  private Struct mergePreservingPopularity(String docName, Struct incoming) {
    if (incoming.containsFields("popularityScore")) {
      return incoming;
    }
    try {
      Document existing =
          documentClient.getDocument(GetDocumentRequest.newBuilder().setName(docName).build());
      com.google.protobuf.Value popularity =
          existing.getStructData().getFieldsOrDefault("popularityScore", null);
      if (popularity == null) {
        return incoming;
      }
      return incoming.toBuilder().putFields("popularityScore", popularity).build();
    } catch (Exception e) {
      // If the merge read fails, fall back to incoming — we accept the
      // popularity-drop side of last-writer-wins rather than propagating
      // a read failure that would abort the whole reindex.
      log.warn(
          "Failed to read existing document for popularity merge ({}): {}",
          docName,
          e.getMessage());
      return incoming;
    }
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
