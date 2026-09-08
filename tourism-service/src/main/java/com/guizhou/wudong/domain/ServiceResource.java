package com.guizhou.wudong.domain;

import java.math.BigDecimal;

public record ServiceResource(
        String id,
        String merchantId,
        String merchantName,
        String name,
        String category,
        String description,
        BigDecimal price,
        String durationText,
        String locationText,
        String tags,
        String imageUrl,
        boolean demoData
) {}
