package com.asoviewclone.analytics.dashboard.dto;

public record RevenueSummaryResponse(
    long totalRevenueJpy,
    long totalOrders,
    double avgOrderValueJpy,
    String periodStart,
    String periodEnd) {}
