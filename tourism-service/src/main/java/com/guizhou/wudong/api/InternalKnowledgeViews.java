package com.guizhou.wudong.api;

import java.util.List;

public final class InternalKnowledgeViews {
    private InternalKnowledgeViews() {
    }

    public record KnowledgeView(
            String id,
            String title,
            String content,
            List<String> tags,
            boolean demoData
    ) {
    }
}
