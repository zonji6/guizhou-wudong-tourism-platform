package com.guizhou.wudong.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Place(
        String id,
        String name,
        String category,
        String description,
        BigDecimal latitude,
        BigDecimal longitude,
        String tags,
        String imageUrl,
        boolean demoData,
        CatalogStatus catalogStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
