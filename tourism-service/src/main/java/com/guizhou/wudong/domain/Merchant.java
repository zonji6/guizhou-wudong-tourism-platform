package com.guizhou.wudong.domain;

import java.time.LocalDateTime;

public record Merchant(
        String id,
        String name,
        String description,
        String contactPhone,
        boolean demoData,
        CatalogStatus catalogStatus,
        LocalDateTime createdAt
) {}
