package com.guizhou.wudong.domain;

import java.time.LocalDateTime;

public record FoodOrder(
        String id,
        String visitorId,
        String foodItemId,
        String foodItemName,
        LocalDateTime visitAt,
        int peopleCount,
        String contactName,
        String contactPhone,
        String note,
        FoodOrderStatus status,
        String sourceThreadId,
        boolean demoData,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
