package com.asoviewclone.analytics.dashboard.dto;

import java.util.List;

public record DailyBookingsResponse(List<DayEntry> days) {

  public record DayEntry(String date, long orderCount, long revenueJpy, double avgOrderValueJpy) {}
}
