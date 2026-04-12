package com.asoviewclone.analytics.writer;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.TableId;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Writes event rows to BigQuery. Uses the standard insertAll API with deduplication on insert_id
 * (event_id). The BigQuery Storage Write API can replace this if sub-second latency or exactly-once
 * semantics are needed.
 */
@Service
public class BigQueryWriterService {

  private static final Logger log = LoggerFactory.getLogger(BigQueryWriterService.class);

  private final BigQuery bigQuery;
  private final String dataset;

  public BigQueryWriterService(
      BigQuery bigQuery, @Value("${analytics.bigquery.dataset:analytics_raw}") String dataset) {
    this.bigQuery = bigQuery;
    this.dataset = dataset;
  }

  /**
   * Insert a single row into the specified table.
   *
   * @param table the BigQuery table name within the analytics_raw dataset
   * @param insertId stable id for deduplication (use event_id from outbox)
   * @param row column-value map
   */
  public void insert(String table, String insertId, Map<String, Object> row) {
    TableId tableId = TableId.of(dataset, table);
    InsertAllRequest request = InsertAllRequest.newBuilder(tableId).addRow(insertId, row).build();

    InsertAllResponse response = bigQuery.insertAll(request);
    if (response.hasErrors()) {
      log.error(
          "BigQuery insert errors for table={} insertId={}: {}",
          table,
          insertId,
          response.getInsertErrors());
    } else {
      log.debug("BigQuery insert ok table={} insertId={}", table, insertId);
    }
  }
}
