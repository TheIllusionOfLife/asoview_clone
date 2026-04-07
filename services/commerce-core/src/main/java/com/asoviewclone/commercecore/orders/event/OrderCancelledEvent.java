package com.asoviewclone.commercecore.orders.event;

public record OrderCancelledEvent(String orderId, String userId) {}
