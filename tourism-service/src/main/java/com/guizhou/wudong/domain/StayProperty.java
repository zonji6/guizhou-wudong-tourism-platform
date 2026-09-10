package com.guizhou.wudong.domain;

import java.time.LocalDateTime;

public record StayProperty(
        String id,
        String merchantId,
        String merchantName,
        String name,
        String description,
        String locationText,
        String tags,
        String imageUrl,
        boolean demoData,
        CatalogStatus catalogStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
