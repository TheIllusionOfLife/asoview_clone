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
  void updatePopularityScoreReadsMergesWritesWholeStruct() {
    // Discovery Engine rejects subpath masks into struct_data, so the service
    // fetches the current struct, overwrites popularityScore only, and writes
    // the merged struct back with mask=["struct_data"].
    Struct existingStruct =
        Struct.newBuilder()
            .putFields(
                "productId",
                com.google.protobuf.Value.newBuilder().setStringValue("prod-789").build())
            .putFields(
                "name",
                com.google.protobuf.Value.newBuilder().setStringValue("Hakone Onsen").build())
            .putFields(
                "popularityScore", com.google.protobuf.Value.newBuilder().setNumberValue(5).build())
            .build();
    Document existing =
        Document.newBuilder().setId("prod-789").setStructData(existingStruct).build();
    when(mockClient.getDocument(any(GetDocumentRequest.class))).thenReturn(existing);

    boolean ok = service.updatePopularityScore("prod-789", 42L);
    assertThat(ok).isTrue();

    ArgumentCaptor<UpdateDocumentRequest> captor =
        ArgumentCaptor.forClass(UpdateDocumentRequest.class);
    verify(mockClient).updateDocument(captor.capture());
    UpdateDocumentRequest req = captor.getValue();

    // Merged struct retains pre-existing fields and updates popularityScore.
    Struct patch = req.getDocument().getStructData();
    assertThat(patch.getFieldsOrThrow("productId").getStringValue()).isEqualTo("prod-789");
    assertThat(patch.getFieldsOrThrow("name").getStringValue()).isEqualTo("Hakone Onsen");
    assertThat(patch.getFieldsOrThrow("popularityScore").getNumberValue()).isEqualTo(42.0);
    // Whole struct_data path — the only supported writable unit.
    assertThat(req.getUpdateMask().getPathsList()).containsExactly("struct_data");
    assertThat(req.getAllowMissing()).isFalse();
    assertThat(req.getDocument().getName())
        .endsWith(
            "projects/proj/locations/global/collections/default_collection/dataStores/asoview-products/branches/default_branch/documents/prod-789");
  }

  @Test
  void markBackfillCompletePropagatesUpsertFailure() {
    doThrow(Status.UNAVAILABLE.asRuntimeException())
        .when(mockClient)
        .createDocument(any(CreateDocumentRequest.class));

    assertThatThrownBy(service::markBackfillComplete)
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("UNAVAILABLE");
  }

  @Test
  void upsertUpdatePathReplacesWholeStructData() {
    // Stub create to throw ALREADY_EXISTS so upsertDocument falls through to update.
    doThrow(new StatusRuntimeException(Status.ALREADY_EXISTS))
        .when(mockClient)
        .createDocument(any(CreateDocumentRequest.class));
    when(mockClient.updateDocument(any(UpdateDocumentRequest.class)))
        .thenReturn(Document.getDefaultInstance());
    // The update path reads the existing doc to preserve popularityScore; the
    // marker doc has none on the server, so return an empty struct.
    when(mockClient.getDocument(any(GetDocumentRequest.class)))
        .thenReturn(Document.getDefaultInstance());

    // markBackfillComplete writes a MARKER doc. Because Discovery Engine
    // rejects subpath masks into struct_data, the update path must use the
    // whole `struct_data` path — this atomically replaces the struct.
    service.markBackfillComplete();

    ArgumentCaptor<UpdateDocumentRequest> captor =
        ArgumentCaptor.forClass(UpdateDocumentRequest.class);
    verify(mockClient).updateDocument(captor.capture());
    UpdateDocumentRequest req = captor.getValue();

    Struct updatedStruct = req.getDocument().getStructData();
    assertThat(updatedStruct.getFieldsOrThrow("productId").getStringValue())
        .isEqualTo("asoview-backfill-marker-v1");
    assertThat(updatedStruct.getFieldsOrThrow("status").getStringValue()).isEqualTo("MARKER");

    // Whole struct_data path — the only supported writable unit.
    assertThat(req.getUpdateMask().getPathsList()).containsExactly("struct_data");
    assertThat(req.getAllowMissing()).isFalse();
  }

  @Test
  void upsertUpdatePathPreservesExistingPopularityScore() {
    // Incoming struct (e.g. from toDoc in the reindex path) carries no
    // popularityScore. The update branch must fetch the existing document and
    // copy its popularityScore into the outgoing struct before writing, so a
    // reindex doesn't wipe the server-side score. Driven here via
    // markBackfillComplete as the simplest upsertDocument caller.
    doThrow(new StatusRuntimeException(Status.ALREADY_EXISTS))
        .when(mockClient)
        .createDocument(any(CreateDocumentRequest.class));
    Struct existingStruct =
        Struct.newBuilder()
            .putFields(
                "popularityScore",
                com.google.protobuf.Value.newBuilder().setNumberValue(77).build())
            .build();
    when(mockClient.getDocument(any(GetDocumentRequest.class)))
        .thenReturn(Document.newBuilder().setStructData(existingStruct).build());
    when(mockClient.updateDocument(any(UpdateDocumentRequest.class)))
        .thenReturn(Document.getDefaultInstance());

    service.markBackfillComplete();

    ArgumentCaptor<UpdateDocumentRequest> captor =
        ArgumentCaptor.forClass(UpdateDocumentRequest.class);
    verify(mockClient).updateDocument(captor.capture());
    Struct merged = captor.getValue().getDocument().getStructData();
    // Incoming fields from markBackfillComplete are preserved…
    assertThat(merged.getFieldsOrThrow("status").getStringValue()).isEqualTo("MARKER");
    // …and popularityScore is carried over from the existing document.
    assertThat(merged.getFieldsOrThrow("popularityScore").getNumberValue()).isEqualTo(77.0);
  }
}
