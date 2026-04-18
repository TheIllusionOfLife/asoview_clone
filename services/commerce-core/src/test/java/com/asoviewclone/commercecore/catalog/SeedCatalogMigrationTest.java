package com.asoviewclone.commercecore.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Validates R__seed_catalog.sql against a real Postgres 16 container. Guards the UUID-stability
 * contract with scripts/seeds/bigquery/004_seed_product_venue_mapping.sql and the product-count /
 * category-distribution assumptions search E2E depends on.
 *
 * <p>Kept as a pure JDBC + Testcontainers test (no @SpringBootTest) so it stays fast: ~5 s startup,
 * no full application context.
 */
class SeedCatalogMigrationTest {

  private static PostgreSQLContainer<?> postgres;

  @BeforeAll
  static void startContainer() {
    postgres = new PostgreSQLContainer<>("postgres:16").withDatabaseName("seed_test");
    postgres.start();

    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("seed_catalog", "true");
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .placeholders(placeholders)
        .load()
        .migrate();
  }

  @AfterAll
  static void stopContainer() {
    if (postgres != null) postgres.stop();
  }

  @Test
  void seedsExactly50Products() throws Exception {
    try (Connection c = conn();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM products")) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(50);
    }
  }

  @Test
  void eachCategoryGetsAtLeastTwelveProducts() throws Exception {
    // 50 / 4 = 12.5, so each category has 12 or 13 products after the mod rotation.
    try (Connection c = conn();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT cat.slug, COUNT(p.id)
                FROM categories cat
                JOIN products p ON p.category_id = cat.id
                GROUP BY cat.slug
                ORDER BY cat.slug
                """)) {
      int rows = 0;
      while (rs.next()) {
        rows++;
        assertThat(rs.getInt(2)).as("category %s product count", rs.getString(1)).isBetween(12, 13);
      }
      assertThat(rows).isEqualTo(4);
    }
  }

  @Test
  void eachVenueGetsAtLeastFiveProducts() throws Exception {
    // 50 / 8 = 6.25, so each venue has 6 or 7 products. Assertion lower-bounds at 5.
    try (Connection c = conn();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT v.name, COUNT(p.id)
                FROM venues v
                JOIN products p ON p.venue_id = v.id
                GROUP BY v.name
                """)) {
      int rows = 0;
      while (rs.next()) {
        rows++;
        assertThat(rs.getInt(2))
            .as("venue %s product count", rs.getString(1))
            .isGreaterThanOrEqualTo(5);
      }
      assertThat(rows).isEqualTo(8);
    }
  }

  @Test
  void productUuidsStayStableWithBigQueryFixture() throws Exception {
    // Sample of UUIDs hardcoded in scripts/seeds/bigquery/004_seed_product_venue_mapping.sql.
    // If the uuid_generate_v5 derivation changes, that BigQuery MERGE breaks.
    String[] pinnedUuids = {
      "c4e00660-a232-5634-9daa-59362df77a59", // product N=1
      "91c66d69-ff03-5c25-b9b4-c039a74babe9", // product N=8
      "c8dc7ef9-9bba-51f5-a48a-e95f0c6749c0", // product N=5
      "0b6eb43a-e6bb-55b3-b290-98954da3d457", // product N=46
      "eec7fb2a-d455-51e7-be38-1295e1fbf300", // product N=50
    };
    try (Connection c = conn();
        PreparedStatement ps =
            c.prepareStatement("SELECT COUNT(*) FROM products WHERE id = ANY(?::uuid[])")) {
      ps.setArray(1, c.createArrayOf("uuid", pinnedUuids));
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        assertThat(rs.getInt(1))
            .as("pinned product UUIDs must remain stable for BigQuery seed compatibility")
            .isEqualTo(pinnedUuids.length);
      }
    }
  }

  @Test
  void japaneseTitlesAreIndexableForCjkSearchTests() throws Exception {
    // The culture product at within-category index 7 (i.e. n=4*7+4=32, culture slot 8)
    // is "Hot Spring Retreat H" / "温泉リトリート H" — used as the CJK anchor in E2E.
    try (Connection c = conn();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT COUNT(*) FROM products WHERE translations->'ja'->>'name' LIKE '%温泉%'")) {
      rs.next();
      assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
    }
  }

  @Test
  void priceSpreadEnablesSortMonotonicityTests() throws Exception {
    // minPrice per product = MIN(price_amount) across variants. Spread must be wide
    // enough that "sort by price asc/desc" produces visibly different first/last hits.
    try (Connection c = conn();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT MAX(min_price) - MIN(min_price)
                FROM (
                  SELECT MIN(price_amount) AS min_price
                  FROM product_variants
                  GROUP BY product_id
                ) t
                """)) {
      rs.next();
      assertThat(rs.getBigDecimal(1).intValue()).isGreaterThan(1000);
    }
  }

  private static Connection conn() throws Exception {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
