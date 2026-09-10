package com.guizhou.wudong.domain;

import java.time.LocalDateTime;

public record ProductOrder(
        String id,
        String visitorId,
        String productId,
        String productName,
        int quantity,
        String pickupPoint,
        String contactName,
        String contactPhone,
        String note,
        ProductOrderStatus status,
        String sourceThreadId,
        boolean demoData,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
