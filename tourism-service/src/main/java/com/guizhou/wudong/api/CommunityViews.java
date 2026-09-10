package com.guizhou.wudong.api;

import com.guizhou.wudong.domain.PostType;
import com.guizhou.wudong.domain.RouteNode;

import java.time.LocalDateTime;
import java.util.List;

public final class CommunityViews {
    private CommunityViews() {
    }

    public record PostView(
            String id,
            String title,
            String content,
            String authorName,
            List<String> tags,
            PostType postType,
            String routeSummary,
            List<RouteNode> routeNodes,
            boolean demoData,
            LocalDateTime publishedAt
    ) {
    }
}
