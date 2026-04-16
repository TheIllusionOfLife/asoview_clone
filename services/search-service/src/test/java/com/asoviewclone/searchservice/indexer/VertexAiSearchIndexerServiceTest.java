package com.asoviewclone.searchservice.indexer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.asoviewclone.searchservice.query.model.ProductDoc;
import com.google.cloud.discoveryengine.v1.Document;
import com.google.cloud.discoveryengine.v1.DocumentServiceClient;
import com.google.cloud.discoveryengine.v1.UpdateDocumentRequest;
import com.google.protobuf.Struct;
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
}
