package com.guizhou.wudong.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Product(
        String id,
        String merchantId,
        String merchantName,
        String name,
        String description,
        BigDecimal price,
        String pickupPoint,
        String tags,
        String imageUrl,
        boolean demoData,
        CatalogStatus catalogStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
