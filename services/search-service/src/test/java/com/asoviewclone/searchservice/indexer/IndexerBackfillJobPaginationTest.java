package com.asoviewclone.searchservice.indexer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestClient;

class IndexerBackfillJobPaginationTest {

  private final IndexerPort indexer = Mockito.mock(IndexerPort.class);
  private final RestClient unusedRestClient = RestClient.builder().baseUrl("http://unused").build();

  @Test
  void skipsWhenMarkerAlreadyPresent() {
    when(indexer.isBackfillComplete()).thenReturn(true);
    IndexerBackfillJob job = new IndexerBackfillJob(indexer, unusedRestClient, true);

    job.run();

    verify(indexer, never()).reindex(anyString());
    verify(indexer, never()).markBackfillComplete();
  }

  @Test
  void paginatesAndMarksCompleteOnFullSuccess() {
    when(indexer.isBackfillComplete()).thenReturn(false);
    doNothing().when(indexer).reindex(anyString());

    IndexerBackfillJob job = stubbedJob(List.of(fakePage(500), fakePage(3)));
    job.run();

    verify(indexer, times(503)).reindex(anyString());
    verify(indexer, times(1)).markBackfillComplete();
  }

  @Test
  void skipsMarkerOnAnyPerDocFailure() {
    when(indexer.isBackfillComplete()).thenReturn(false);
    doThrow(new RuntimeException("boom"))
        .doNothing()
        .doNothing()
        .when(indexer)
        .reindex(anyString());

    IndexerBackfillJob job = stubbedJob(List.of(fakePage(3)));
    job.run();

    verify(indexer, times(3)).reindex(anyString());
    verify(indexer, never()).markBackfillComplete();
  }

  @Test
  void disabledFlagIsNoop() {
    IndexerBackfillJob job = new IndexerBackfillJob(indexer, unusedRestClient, false);
    job.run();
    verify(indexer, never()).isBackfillComplete();
    verify(indexer, never()).reindex(anyString());
    verify(indexer, never()).markBackfillComplete();
  }

  private IndexerBackfillJob stubbedJob(List<String> pages) {
    Iterator<String> it = pages.iterator();
    return new IndexerBackfillJob(indexer, unusedRestClient, true) {
      @Override
      protected String fetchPage(int page, int size) {
        return it.hasNext() ? it.next() : "{\"content\":[]}";
      }
    };
  }

  private static String fakePage(int count) {
    StringBuilder sb = new StringBuilder("{\"content\":[");
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append("{\"id\":\"p-").append(i).append("\"}");
    }
    sb.append("]}");
    return sb.toString();
  }
}
