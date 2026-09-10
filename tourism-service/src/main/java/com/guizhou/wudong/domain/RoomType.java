package com.guizhou.wudong.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RoomType(
        String id,
        String stayPropertyId,
        String stayPropertyName,
        String name,
        String description,
        int maxGuests,
        BigDecimal price,
        String imageUrl,
        boolean demoData,
        CatalogStatus catalogStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
