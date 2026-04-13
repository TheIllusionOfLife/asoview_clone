package com.asoviewclone.analytics.dashboard;

import com.asoviewclone.analytics.dashboard.dto.DailyBookingsResponse;
import com.asoviewclone.analytics.dashboard.dto.ProductRankingResponse;
import com.asoviewclone.analytics.dashboard.dto.RevenueSummaryResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// TODO: add admin authorization guard once analytics-ingest has its own auth filter or
// the gateway enforces ROLE_ADMIN for /v1/admin/** paths (post-merge recommendation).
@RestController
@RequestMapping("/v1/admin/analytics")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @GetMapping("/daily-bookings")
  public DailyBookingsResponse getDailyBookings(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to)
      throws InterruptedException {
    if (from.isAfter(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
    }
    return dashboardService.getDailyBookings(from.toString(), to.toString());
  }

  @GetMapping("/product-ranking")
  public ProductRankingResponse getProductRanking(@RequestParam(defaultValue = "10") int limit)
      throws InterruptedException {
    int safeLimit = Math.max(1, Math.min(limit, 100));
    return dashboardService.getProductRanking(safeLimit);
  }

  @GetMapping("/revenue-summary")
  public RevenueSummaryResponse getRevenueSummary() throws InterruptedException {
    return dashboardService.getRevenueSummary();
  }
}
