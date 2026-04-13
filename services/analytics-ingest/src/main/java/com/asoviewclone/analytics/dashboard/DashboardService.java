package com.asoviewclone.analytics.dashboard;

import com.asoviewclone.analytics.dashboard.dto.DailyBookingsResponse;
import com.asoviewclone.analytics.dashboard.dto.ProductRankingResponse;
import com.asoviewclone.analytics.dashboard.dto.RevenueSummaryResponse;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.TableResult;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

  private static final long QUERY_TIMEOUT_MS = 30_000L;

  private final BigQuery bigQuery;
  private final String project;

  public DashboardService(
      BigQuery bigQuery, @Value("${analytics.bigquery.project:asoview-clone-dev}") String project) {
    this.bigQuery = bigQuery;
    this.project = project;
  }

  public DailyBookingsResponse getDailyBookings(String from, String to)
      throws InterruptedException {
    String sql =
        """
        SELECT booking_date, order_count, revenue_jpy, avg_order_value_jpy
        FROM `%s.analytics_mart.daily_bookings`
        WHERE booking_date >= @from_date AND booking_date <= @to_date
        ORDER BY booking_date DESC
        """
            .formatted(project);

    QueryJobConfiguration config =
        QueryJobConfiguration.newBuilder(sql)
            .addNamedParameter("from_date", QueryParameterValue.date(from))
            .addNamedParameter("to_date", QueryParameterValue.date(to))
            .setUseLegacySql(false)
            .setJobTimeoutMs(QUERY_TIMEOUT_MS)
            .build();

    TableResult result = bigQuery.query(config);
    List<DailyBookingsResponse.DayEntry> days = new ArrayList<>();
    for (FieldValueList row : result.iterateAll()) {
      days.add(
          new DailyBookingsResponse.DayEntry(
              row.get("booking_date").getStringValue(),
              row.get("order_count").isNull() ? 0 : row.get("order_count").getLongValue(),
              row.get("revenue_jpy").isNull() ? 0 : row.get("revenue_jpy").getLongValue(),
              row.get("avg_order_value_jpy").isNull()
                  ? 0.0
                  : row.get("avg_order_value_jpy").getDoubleValue()));
    }
    return new DailyBookingsResponse(days);
  }

  public ProductRankingResponse getProductRanking(int limit) throws InterruptedException {
    String sql =
        """
        SELECT product_id, order_count, total_revenue_jpy, popularity_rank
        FROM `%s.analytics_mart.product_ranking`
        ORDER BY popularity_rank ASC
        LIMIT @limit
        """
            .formatted(project);

    QueryJobConfiguration config =
        QueryJobConfiguration.newBuilder(sql)
            .addNamedParameter("limit", QueryParameterValue.int64(limit))
            .setUseLegacySql(false)
            .setJobTimeoutMs(QUERY_TIMEOUT_MS)
            .build();

    TableResult result = bigQuery.query(config);
    List<ProductRankingResponse.RankedProduct> products = new ArrayList<>();
    for (FieldValueList row : result.iterateAll()) {
      products.add(
          new ProductRankingResponse.RankedProduct(
              row.get("product_id").getStringValue(),
              row.get("order_count").isNull() ? 0 : row.get("order_count").getLongValue(),
              row.get("total_revenue_jpy").isNull()
                  ? 0
                  : row.get("total_revenue_jpy").getLongValue(),
              row.get("popularity_rank").isNull() ? 0 : row.get("popularity_rank").getLongValue()));
    }
    return new ProductRankingResponse(products);
  }

  public RevenueSummaryResponse getRevenueSummary() throws InterruptedException {
    String sql =
        """
        SELECT
          SUM(revenue_jpy) AS total_revenue,
          SUM(order_count) AS total_orders,
          SAFE_DIVIDE(SUM(revenue_jpy), SUM(order_count)) AS avg_order_value,
          MIN(booking_date) AS period_start,
          MAX(booking_date) AS period_end
        FROM `%s.analytics_mart.daily_bookings`
        """
            .formatted(project);

    QueryJobConfiguration config =
        QueryJobConfiguration.newBuilder(sql)
            .setUseLegacySql(false)
            .setJobTimeoutMs(QUERY_TIMEOUT_MS)
            .build();

    TableResult result = bigQuery.query(config);
    Iterator<FieldValueList> it = result.iterateAll().iterator();
    if (!it.hasNext()) {
      return new RevenueSummaryResponse(0, 0, 0.0, "", "");
    }
    FieldValueList row = it.next();
    return new RevenueSummaryResponse(
        row.get("total_revenue").isNull() ? 0 : row.get("total_revenue").getLongValue(),
        row.get("total_orders").isNull() ? 0 : row.get("total_orders").getLongValue(),
        row.get("avg_order_value").isNull() ? 0.0 : row.get("avg_order_value").getDoubleValue(),
        row.get("period_start").isNull() ? "" : row.get("period_start").getStringValue(),
        row.get("period_end").isNull() ? "" : row.get("period_end").getStringValue());
  }
}
