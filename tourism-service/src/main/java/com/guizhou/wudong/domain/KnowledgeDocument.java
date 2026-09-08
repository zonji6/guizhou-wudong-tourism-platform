package com.guizhou.wudong.domain;

import java.time.LocalDateTime;

public record KnowledgeDocument(
        String id,
        String title,
        String content,
        String sourceType,
        String tags,
        boolean published,
        String indexStatus,
        boolean demoData,
        LocalDateTime updatedAt
) {}
