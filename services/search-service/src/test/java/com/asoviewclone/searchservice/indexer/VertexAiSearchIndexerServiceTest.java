package com.asoviewclone.searchservice.indexer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asoviewclone.searchservice.query.model.ProductDoc;
import com.google.cloud.discoveryengine.v1.CreateDocumentRequest;
import com.google.cloud.discoveryengine.v1.Document;
import com.google.cloud.discoveryengine.v1.DocumentServiceClient;
import com.google.cloud.discoveryengine.v1.GetDocumentRequest;
import com.google.cloud.discoveryengine.v1.UpdateDocumentRequest;
import com.google.protobuf.Struct;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.client.RestClient;

class VertexAiSearchIndexerServiceTest {

  private final DocumentServiceClient mockClient = Mockito.mock(DocumentServiceClient.class);
  private final RestClient unusedRestClient = RestClient.builder().baseUrl("http://unused").build();
  private final VertexAiSearchIndexerService service =
      new VertexAiSearchIndexerService(
          mockClient,
          unusedRestClient,
          "projects/proj/locations/global/collections/default_collection/dataStores/asoview-products/branches/default_branch");

  @Test
  void documentUsesProductIdAsNativeIdAndStructField() {
    ProductDoc doc =
        new ProductDoc(
            "prod-123",
            "Hakone Onsen Day Trip",
            "Relax in a hot spring",
            "area-kanto",
            "cat-spa",
            3500L,
            "ACTIVE",
            "2026-04-17T10:00:00Z",
            17L);
    Document document = service.toDocument(doc);

    // Discovery Engine document id matches the upstream productId.
    assertThat(document.getId()).isEqualTo("prod-123");

    Struct data = document.getStructData();
    assertThat(data.getFieldsOrThrow("productId").getStringValue()).isEqualTo("prod-123");
    assertThat(data.getFieldsOrThrow("name").getStringValue()).isEqualTo("Hakone Onsen Day Trip");
    assertThat(data.getFieldsOrThrow("description").getStringValue())
        .isEqualTo("Relax in a hot spring");
    assertThat(data.getFieldsOrThrow("areaId").getStringValue()).isEqualTo("area-kanto");
    assertThat(data.getFieldsOrThrow("categoryId").getStringValue()).isEqualTo("cat-spa");
    assertThat(data.getFieldsOrThrow("status").getStringValue()).isEqualTo("ACTIVE");
    assertThat(data.getFieldsOrThrow("minPrice").getNumberValue()).isEqualTo(3500.0);
    assertThat(data.getFieldsOrThrow("popularityScore").getNumberValue()).isEqualTo(17.0);
  }

  @Test
  void nullOptionalFieldsAreOmitted() {
    ProductDoc doc =
        new ProductDoc(
            "prod-456", "Minimal", null, null, null, null, "ACTIVE", "2026-04-17T10:00:00Z", null);
    Document document = service.toDocument(doc);
    Struct data = document.getStructData();
    assertThat(data.containsFields("description")).isFalse();
    assertThat(data.containsFields("areaId")).isFalse();
    assertThat(data.containsFields("categoryId")).isFalse();
    assertThat(data.containsFields("minPrice")).isFalse();
    assertThat(data.containsFields("popularityScore")).isFalse();
    assertThat(data.getFieldsOrThrow("productId").getStringValue()).isEqualTo("prod-456");
    assertThat(data.getFieldsOrThrow("name").getStringValue()).isEqualTo("Minimal");
    assertThat(data.getFieldsOrThrow("status").getStringValue()).isEqualTo("ACTIVE");
  }

  @Test
  void updatePopularityScoreSendsOnlyPopularityFieldWithFieldMask() {
    boolean ok = service.updatePopularityScore("prod-789", 42L);
    assertThat(ok).isTrue();

    ArgumentCaptor<UpdateDocumentRequest> captor =
        ArgumentCaptor.forClass(UpdateDocumentRequest.class);
    verify(mockClient).updateDocument(captor.capture());
    UpdateDocumentRequest req = captor.getValue();

    // The patch struct only carries popularityScore; no other fields to clobber.
    Struct patch = req.getDocument().getStructData();
    assertThat(patch.getFieldsMap().keySet()).containsExactly("popularityScore");
    assertThat(patch.getFieldsOrThrow("popularityScore").getNumberValue()).isEqualTo(42.0);
    // FieldMask scopes the server-side write to the popularityScore path.
    assertThat(req.getUpdateMask().getPathsList()).containsExactly("struct_data.popularityScore");
    assertThat(req.getAllowMissing()).isFalse();
    assertThat(req.getDocument().getName())
        .endsWith(
            "projects/proj/locations/global/collections/default_collection/dataStores/asoview-products/branches/default_branch/documents/prod-789");
  }

  @Test
  void markBackfillCompletePropagatesUpsertFailure() {
    doThrow(new RuntimeException("discovery engine down")).when(mockClient).createDocument(any());

    assertThatThrownBy(service::markBackfillComplete)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("discovery engine down");
  }

  @Test
  void upsertUpdatePathPreservesExistingPopularityScore() {
    // Simulate ALREADY_EXISTS on create so upsertDocument falls through to update.
    doThrow(new StatusRuntimeException(Status.ALREADY_EXISTS))
        .when(mockClient)
        .createDocument(any(CreateDocumentRequest.class));

    // The existing document in Discovery Engine has a popularityScore written by the sync job.
    Struct existingStruct =
        Struct.newBuilder()
            .putFields(
                "popularityScore",
                com.google.protobuf.Value.newBuilder().setNumberValue(99.0).build())
            .putFields(
                "name", com.google.protobuf.Value.newBuilder().setStringValue("Old name").build())
            .build();
    String docName =
        "projects/proj/locations/global/collections/default_collection/dataStores/asoview-products/branches/default_branch/documents/prod-merge";
    Document existingDoc =
        Document.newBuilder()
            .setName(docName)
            .setId("prod-merge")
            .setStructData(existingStruct)
            .build();
    when(mockClient.getDocument(any(GetDocumentRequest.class))).thenReturn(existingDoc);

    // toDocument omits popularityScore when it is null (as toDoc now sets it).
    ProductDoc doc =
        new ProductDoc(
            "prod-merge", "New name", null, null, null, null, "ACTIVE", "2026-04-17", null);
    Document incoming = service.toDocument(doc);
    assertThat(incoming.getStructData().containsFields("popularityScore")).isFalse();

    // Trigger the upsert (reindex calls upsertDocument internally, but we test
    // the package-private toDocument + direct mock path to isolate the merge logic).
    // We call markBackfillComplete with a crafted marker, but it's simpler to
    // invoke reindex indirectly. Instead, test the update capture directly.
    // Use the updatePopularityScore path? No, that's separate. Let's just call
    // the service via the public reindex, mocking the RestClient.

    // Actually: the cleanest way is to build the document and call the mock-verified
    // update path. Since upsertDocument is private, we verify via the mock captures.
    // Re-stub createDocument to throw ALREADY_EXISTS, getDocument returns existing,
    // then verify updateDocument merges the score.

    // Trigger: call reindex with a mock commerce-core response.
    // This test is already complex, so let's verify the struct merge directly.
    // The service.toDocument gives us a Document without popularityScore.
    // We need to trigger upsertDocument. Use markBackfillComplete as it's public
    // and calls upsertDocument. We already tested failure propagation; now test merge.

    // Reset stubs for a clean run.
    Mockito.reset(mockClient);
    doThrow(new StatusRuntimeException(Status.ALREADY_EXISTS))
        .when(mockClient)
        .createDocument(any(CreateDocumentRequest.class));
    when(mockClient.getDocument(any(GetDocumentRequest.class))).thenReturn(existingDoc);
    when(mockClient.updateDocument(any(UpdateDocumentRequest.class))).thenReturn(existingDoc);

    // markBackfillComplete writes a MARKER doc with no popularityScore.
    // On the update path it should pick up the existing doc's popularityScore.
    service.markBackfillComplete();

    ArgumentCaptor<UpdateDocumentRequest> captor =
        ArgumentCaptor.forClass(UpdateDocumentRequest.class);
    verify(mockClient).updateDocument(captor.capture());
    Struct mergedStruct = captor.getValue().getDocument().getStructData();
    // The marker doc's own fields are present.
    assertThat(mergedStruct.getFieldsOrThrow("productId").getStringValue())
        .isEqualTo("asoview-backfill-marker-v1");
    // popularityScore from the existing doc is preserved.
    assertThat(mergedStruct.getFieldsOrThrow("popularityScore").getNumberValue()).isEqualTo(99.0);
  }
}
