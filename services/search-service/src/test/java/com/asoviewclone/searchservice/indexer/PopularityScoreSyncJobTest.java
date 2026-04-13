package com.asoviewclone.searchservice.indexer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class PopularityScoreSyncJobTest {

  @Test
  void sync_updatesPopularityScoresFromBigQuery() throws Exception {
    BigQuery bigQuery = mock(BigQuery.class);
    IndexerService indexerService = mock(IndexerService.class);

    Schema schema =
        Schema.of(
            Field.of("product_id", StandardSQLTypeName.STRING),
            Field.of("order_count", StandardSQLTypeName.INT64));

    FieldValueList row =
        FieldValueList.of(
            List.of(
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "product-1"),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "5")),
            schema.getFields());

    TableResult tableResult = mock(TableResult.class);
    when(tableResult.iterateAll()).thenReturn(List.of(row));
    when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(tableResult);

    PopularityScoreSyncJob job = new PopularityScoreSyncJob(bigQuery, indexerService, "test-proj");
    job.sync();

    verify(indexerService).updatePopularityScore("product-1", 5L);
  }

  @Test
  void sync_handlesEmptyResult() throws Exception {
    BigQuery bigQuery = mock(BigQuery.class);
    IndexerService indexerService = mock(IndexerService.class);

    TableResult tableResult = mock(TableResult.class);
    when(tableResult.iterateAll()).thenReturn(List.of());
    when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(tableResult);

    PopularityScoreSyncJob job = new PopularityScoreSyncJob(bigQuery, indexerService, "test-proj");
    job.sync();
    // No exception, no calls to indexerService
  }
}
