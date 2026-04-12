package com.asoviewclone.analytics.dashboard;

import com.asoviewclone.analytics.dashboard.dto.DailyBookingsResponse;
import com.asoviewclone.analytics.dashboard.dto.ProductRankingResponse;
import com.asoviewclone.analytics.dashboard.dto.RevenueSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/analytics")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @GetMapping("/daily-bookings")
  public DailyBookingsResponse getDailyBookings(@RequestParam String from, @RequestParam String to)
      throws InterruptedException {
    return dashboardService.getDailyBookings(from, to);
  }

  @GetMapping("/product-ranking")
  public ProductRankingResponse getProductRanking(@RequestParam(defaultValue = "10") int limit)
      throws InterruptedException {
    return dashboardService.getProductRanking(limit);
  }

  @GetMapping("/revenue-summary")
  public RevenueSummaryResponse getRevenueSummary() throws InterruptedException {
    return dashboardService.getRevenueSummary();
  }
}
