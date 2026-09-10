package com.guizhou.wudong.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FoodItem(
        String id,
        String merchantId,
        String merchantName,
        String name,
        String description,
        BigDecimal price,
        String visitTimeText,
        String tags,
        String imageUrl,
        boolean demoData,
        CatalogStatus catalogStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
