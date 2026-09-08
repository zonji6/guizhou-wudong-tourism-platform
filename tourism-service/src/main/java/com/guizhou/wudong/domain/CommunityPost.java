package com.guizhou.wudong.domain;

import java.time.LocalDateTime;

public record CommunityPost(
        String id,
        String title,
        String content,
        String authorName,
        String coverUrl,
        String tags,
        boolean demoData,
        LocalDateTime publishedAt
) {}
