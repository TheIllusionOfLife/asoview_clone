package com.asoviewclone.analytics.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.asoviewclone.analytics.dashboard.dto.DailyBookingsResponse;
import com.asoviewclone.analytics.dashboard.dto.ProductRankingResponse;
import com.asoviewclone.analytics.dashboard.dto.RevenueSummaryResponse;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

  private BigQuery bigQuery;
  private DashboardService service;

  @BeforeEach
  void setUp() {
    bigQuery = mock(BigQuery.class);
    service = new DashboardService(bigQuery, "test-project");
  }

  @Test
  void getDailyBookings_returnsEntries() throws Exception {
    FieldList fields =
        FieldList.of(
            Field.of("booking_date", StandardSQLTypeName.STRING),
            Field.of("order_count", StandardSQLTypeName.INT64),
            Field.of("revenue_jpy", StandardSQLTypeName.INT64),
            Field.of("avg_order_value_jpy", StandardSQLTypeName.FLOAT64));
    FieldValueList row =
        FieldValueList.of(
            List.of(
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "2026-04-01"),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "5"),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "25000"),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "5000.0")),
            fields);

    TableResult result = mock(TableResult.class);
    when(result.iterateAll()).thenReturn(List.of(row));
    when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(result);

    DailyBookingsResponse response = service.getDailyBookings("2026-04-01", "2026-04-13");

    assertThat(response.days()).hasSize(1);
    assertThat(response.days().getFirst().date()).isEqualTo("2026-04-01");
    assertThat(response.days().getFirst().orderCount()).isEqualTo(5);
    assertThat(response.days().getFirst().revenueJpy()).isEqualTo(25000);
  }

  @Test
  void getProductRanking_returnsRankedProducts() throws Exception {
    FieldList fields =
        FieldList.of(
            Field.of("product_id", StandardSQLTypeName.STRING),
            Field.of("order_count", StandardSQLTypeName.INT64),
            Field.of("total_revenue_jpy", StandardSQLTypeName.INT64),
            Field.of("popularity_rank", StandardSQLTypeName.INT64));
    FieldValueList row =
        FieldValueList.of(
            List.of(
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "product-1"),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "10"),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "50000"),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "1")),
            fields);

    TableResult result = mock(TableResult.class);
    when(result.iterateAll()).thenReturn(List.of(row));
    when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(result);

    ProductRankingResponse response = service.getProductRanking(10);

    assertThat(response.products()).hasSize(1);
    assertThat(response.products().getFirst().productId()).isEqualTo("product-1");
    assertThat(response.products().getFirst().popularityRank()).isEqualTo(1);
  }

  @Test
  void getRevenueSummary_returnsSummary() throws Exception {
    FieldList fields =
        FieldList.of(
            Field.of("total_revenue", StandardSQLTypeName.INT64),
            Field.of("total_orders", StandardSQLTypeName.INT64),
            Field.of("avg_order_value", StandardSQLTypeName.FLOAT64),
            Field.of("period_start", StandardSQLTypeName.STRING),
            Field.of("period_end", StandardSQLTypeName.STRING));
    FieldValueList row =
        FieldValueList.of(
            List.of(
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "500000"),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "80"),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "6250.0"),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "2026-01-15"),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "2026-04-13")),
            fields);

    TableResult result = mock(TableResult.class);
    when(result.iterateAll()).thenReturn(List.of(row));
    when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(result);

    RevenueSummaryResponse response = service.getRevenueSummary();

    assertThat(response.totalRevenueJpy()).isEqualTo(500000);
    assertThat(response.totalOrders()).isEqualTo(80);
    assertThat(response.periodStart()).isEqualTo("2026-01-15");
  }
}
